(ns almanac.storage
  (:require [korma.db :refer [defdb mysql transaction rollback]]
            [clojure.string :as string]
            [clojure.set :as set]
            [environ.core :refer [env]]
            [almanac.utils :as utils]
            [almanac.cache :as cache :refer [KeyValueStore]]
            [zolo.utils.clojure :as zutils]
            [clojure.tools.logging :as log]
            [slingshot.slingshot :refer [try+ throw+]])
  (:use [korma.core]))

(defdb rds-db (mysql {:user (:rds-user env)
                      :password (:rds-pass env)
                      :host (:rds-host env)
                      :db (:rds-db env)}))

(declare person profile photo)

(defentity person
  (pk :id)
  (database rds-db)
  (entity-fields :email :last_updated_at)
  (has-many profile)
  (has-many photo))

(defentity profile
  (pk :id)
  (database rds-db)
  (entity-fields :service :username :url)
  (belongs-to person))

(defentity photo
  (pk :id)
  (database rds-db)
  (entity-fields :service :url)
  (belongs-to person))

(defn- get-entity-id [entity where-clause]
  (let [pk (:pk entity)]
    (-> (select entity
                (fields pk)
                (where where-clause))
        (first)
        (pk))))

;; TODO: generated key seems to be MySQL specific key
;;       have to check this and replace with a correct insertion
(defn- get-or-create [entity where-clause default]
  (if-let [existing-id (get-entity-id entity where-clause)]
    existing-id
    (:GENERATED_KEY (insert entity
                            (values default)))))

;; it has to be a macro because of the way korma.core/fields works
(defmacro subentities-for-person-id [entity select-fields person-id]
  `(select ~entity
           (fields ~@select-fields)
           (where {:person_id ~person-id})))

(defn get-info [email]
  (if-let [person-id (get-entity-id person {:email email})]
    (let [photos (utils/group-by-and-compacted
                  :service
                  #(select-keys % [:url])
                  (subentities-for-person-id photo [:service :url] person-id))
          profiles (utils/group-by-and-compacted
                    :service
                    #(select-keys % [:url :username])
                    (subentities-for-person-id profile [:service :url :username] person-id))]
      {:services (vec (set/union (set (keys profiles)) (set (keys photos))))
       :socialProfiles profiles
       :photos photos})
    nil))

(defn- gen-id-fn-from-fields [& args]
  (fn [item]
    (apply str (map #(get item %) args))))

(defn- diff-service-with-db [service-data db-data id-fn]
  (let [new-data (mapcat (fn [service-name]
                               (map #(assoc % :service service-name)
                                    (get service-data service-name)))
                             (keys service-data))]
    (zutils/diff db-data new-data id-fn)))

;; TODO: 2 more optimizations
;; 1. On delete I can use macro to generate a huge (or ...) so it will run in 1 SQL request
;; 2. On remaining items I actually should check and store only changed ones
(defn- store-diff-as-entity [diff entity person-id]
  (insert entity
          (values (mapv #(assoc % :person_id person-id)
                        (:added diff))))
  (doseq [d (:deleted diff)]
    (delete entity
            (where {:person_id person-id})
            (where d)))
  (doseq [d (:remaining diff)]
    (update entity
            (set-fields d)
            (where {:person_id person-id})
            (where d))))

;; still macro as subentities-for-person-id is macro and requires entity-fields
;; transferred without modifications
(defmacro sync-service-entity [entity person-id entity-fields id-fields service-data]
  `(let [id-fn# (gen-id-fn-from-fields ~@id-fields)
         entities# (subentities-for-person-id ~entity ~entity-fields ~person-id)
         diff# (diff-service-with-db ~service-data entities# id-fn#)]
     (store-diff-as-entity diff# ~entity ~person-id)))

(defn store-info [email info]
  (transaction
   (try+
    (let [person-id (get-or-create person {:email email} {:email email})]
      (sync-service-entity photo
                           person-id
                           [:service :url]
                           [:service :url]
                           (:photos info))
      (sync-service-entity profile
                           person-id
                           [:service :url :username]
                           [:service :username]
                           (:socialProfiles info)))
    (catch Object e
      (rollback)
      (log/error (format "Failed to store person %s, reason: %s" email e))))))

(defrecord SQLStore [config]
    KeyValueStore
    (get-value [_ key] (get-info key))
    (set-value [_ key value] (store-info key value)))

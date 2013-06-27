(ns almanac.storage
  (:require [korma.db :refer [defdb mysql transaction rollback]]
            [clojure.string :as string]
            [clojure.set :as set]
            [environ.core :refer [env]]
            [almanac.utils :as utils]
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

(defn get-entity-id [entity where-clause]
  (let [pk (:pk entity)]
    (-> (select entity
                (fields pk)
                (where where-clause))
        (first)
        (pk))))

;; TODO: generated key seems to be MySQL specific key
;;       have to check this and replace with a correct insertion
(defn get-or-create [entity where-clause default]
  (if-let [existing-id (get-entity-id entity where-clause)]
    existing-id
    (:GENERATED_KEY (insert entity
                            (values default)))))

(defn get-info [email]
  (if-let [person-id (get-entity-id person {:email email})]
    (let [photos (utils/group-by-and-compacted :service
                                               #(select-keys % [:url])
                                               (select photo
                                                       (fields :service :url)
                                                       (where {:person_id person-id})))
          profiles (utils/group-by-and-compacted :service
                                                 #(select-keys % [:url :username])
                                                 (select profile
                                                         (fields :service :url :username)
                                                         (where {:person_id person-id})))]
      {:services (vec (set/union (set (keys profiles)) (set (keys photos))))
       :socialProfiles profiles
       :photos photos})
    nil))

;; TODO: 2 more optimizations
;; 1. On delete I can use macro to generate a huge (or ...) so it will run in 1 SQL request
;; 2. On remaining items I actually should check and store only changed ones
(defmacro sync-service-entity [entity person-id entity-fields id-fields new-data]
  `(let [service-data# (mapcat (fn [service-name#]
                                (map #(assoc % :service service-name#)
                                     (get ~new-data service-name#)))
                               (keys ~new-data))
         ex-data# (vec
                   (map identity
                    (select ~entity
                            (fields ~@entity-fields)
                            (where {:person_id ~person-id}))))
         diff# (zutils/diff ex-data# service-data#
                            (fn [v#]
                              (apply str (map #(get v# %) ~id-fields))))]
     (insert ~entity
             (values (mapv #(assoc % :person_id ~person-id)
                           (:added diff#))))
     (doseq [d# (:deleted diff#)]
       (delete ~entity
               (where {:person_id ~person-id})
               (where d#)))
     (doseq [d# (:remaining diff#)]
       (update ~entity
               (set-fields d#)
               (where {:person_id ~person-id})
               (where d#)))))

(defn store-info [email info]
  (transaction
   (try+
    (let [person-id (get-or-create person {:email email} {:email email})]
      (sync-service-entity photo person-id [:service :url] [:service :url] (:photos info))
      (sync-service-entity profile person-id [:service :url :username] [:service :username] (:socialProfiles info)))
    (catch Object e
      (rollback)
      (log/error (format "Failed to store person %s, reason: %s" email e))))))

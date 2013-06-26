(ns almanac.storage
  (:require [korma.db :as db :refer [defdb mysql]]
            [clojure.string :as string]
            [clojure.set :refer [union]]
            [korma.core :as kcore :refer [defentity pk database
                                          entity-fields belongs-to
                                          select insert update
                                          values has-many where
                                          fields delete set-fields]]
            [environ.core :refer [env]]
            [almanac.utils :as utils]
            [zolo.utils.clojure :as zutils]))

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

(defmacro get-entity-id [entity where-clause]
  `((:pk ~entity)
    (first
     (select ~entity
             (fields (:pk ~entity))
             (where ~where-clause)))))

;; TODO: generated key seems to be MySQL specific key
;;       have to check this and replace with a correct insertion
(defmacro get-or-create [entity where-clause default]
  `(if-let [existing-id# (get-entity-id ~entity ~where-clause)]
     existing-id#
     (:GENERATED_KEY (insert ~entity
                             (values ~default)))))

(defn- entity->map [keys]
  (fn [entity]
    (select-keys entity keys)))

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
      {:services (vec (union (set (keys profiles)) (set (keys photos))))
       :socialProfiles profiles
       :photos photos})
    nil))

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
     (doseq [d# (:added diff#)]
       (insert ~entity
               (values (assoc d# :person_id ~person-id))))
     (doseq [d# (:deleted diff#)]
       (delete ~entity
               (where (assoc d# :person_id ~person-id))))
     (doseq [d# (:remaining diff#)]
       (update ~entity
               (set-fields d#)
               (where (assoc d# :person_id ~person-id))))))

(defn store-info [email info]
  (let [person-id (get-or-create person {:email email} {:email email})]
    (sync-service-entity photo person-id [:service :url] [:service :url] (:photos info))
    (sync-service-entity profile person-id [:service :url :username] [:service :username] (:socialProfiles info))))

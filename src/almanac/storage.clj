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

;;; Social storage hides DB implementation for storing all the activity data
;;; The client should access user activity data by running queries
;;; Data should be pushed into DB by a cron-like daemon which will
;;; use corresponding service adapters

;; STUB
;; (defmethod update-activity :twitter [storage user-id network]
;;   (add-items storage user-id :twitter (map twitter->ActivityItem (twitter/get-mentions (get-credentials storage :twitter user-id))))
;;   (set-last-update storage user-id :twitter (Date.))

;; (defn update-activities [storage email networks]
;;  (let [profiles (:socialProfiles (get-profiles-by-email storage email))]
;;    (mapcat #(update-activity % (:% profiles) storage) networks))

;;; TODO: Fullcontact should be migrated to use this storage

(defprotocol SocialStorage
  (get-profiles-by-email [storage email]) ;; returns social profiles using email lookup
  (get-profiles-by-network-id [storage user-id network]) ;; returns social profiles using existing profile user-id in network
  ;;; TODO: may be credentials should be provided by a separate Thing
  (get-credentials [storage network user-id]) ;; map with required credentials info (user/password or OAuth token and so on)
  (set-credentials [storage network user-id new-credentials]) ;; see above
  (add-items [storage user-id network items]) ;; adds all the activity items for a user from a specific network
  (get-items [storage user-id network]) ;; gets all the activity items for a user from a specific network
  (get-conversation-items [storage current-user-id companion-user-id network]) ;; gets conversations between current-user-id and companion-user-id
  (get-last-update [storage user-id network]) ;; might be interesting later for service adapters
  (set-last-update [storage user-id network timestamp])) ;; migth be interesting later for service adapters

;;; Activity item is a map
;;; Possible keys are:
;;;    :network-type - network type, :twitter, :facebook, :gplus and so on
;;;    :user-id- user-id (specific for network) of user on behalf the data was pulled
;;;    :companion-id - user-id (specific for network) of conversation companion
;;;    :content - string
;;;    :stamp - network specific stamp
;;;    :message-type (optional) can be :message or :mention or any other value
;;;    :link (optional) direct URL to the message
;;;    :thread-id (optional) string can point to whole thread if it is possible
;;;    other keys as required...


(defn mem-storage
  ([] (mem-storage {}))
  ([initial-data]
     (let [data (atom (merge {} initial-data))]
       (reify SocialStorage
         (get-profiles-by-email [_ email]
           "Not implemented")
         (get-profiles-by-network-id [_ user-id network]
           "Not implemented")
         (get-credentials [_ network user-id]
           (get-in @data [:credentials network user-id]))
         (set-credentials [_ network user-id new-credentials]
           (swap! data update-in [:credentials network user-id] (constantly new-credentials)))
         (add-items [_ user-id network items]
           (swap! data update-in [:items network user-id] #(concat (or % []) items)))
         (get-items [_ user-id network]
           (get-in @data [:items network user-id]))
         (get-conversation-items [storage current-user-id companion-user-id network]
           (filter #(constantly %) (get-items storage current-user-id network)))
         (get-last-update [_ user-id network]
           (get-in @data [:updates network user-id]))
         (set-last-update [_ user-id network timestamp]
           (swap! data update-in [:updates network user-id] (constantly timestamp)))))))

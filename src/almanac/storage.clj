(ns almanac.storage
  (:require [slingshot.slingshot :refer [try+ throw+]]))

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

(defprotocol SocialStorage
  ;;; TODO: may be credentials should be provided by a separate Thing
  (get-credentials [storage network user-id]) ;; map with required credentials info (user/password or OAuth token and so on)
  (set-credentials [storage network user-id new-credentials]) ;; see above
  (add-items [storage items]) ;; adds all the activity items for a user from a specific network
  (get-items [storage]) ;; gets all the activity items for a user from a specific network
  (get-items-for-user [storage user-id network])
  (get-user-posts [storage user-id network])
  (get-conversation-items [storage current-user-id companion-user-id network]) ;; gets conversations between current-user-id and companion-user-id
;;  (get-last-update [storage user-id network]) ;; might be interesting later for service adapters
;;  (set-last-update [storage user-id network timestamp])
) ;; migth be interesting later for service adapters

;;; Activity item is a map
;;; Possible keys are:
;;;    :network-type - network type, :twitter, :facebook, :gplus and so on
;;;    :sender-id - user-id of item author
;;;    :recipients - set of user-ids of recipients/mentioned persons
;;;    :content - string
;;;    :created-time - date
;;;    :message-type (optional) can be :message or :mention or any other value
;;;    :link (optional) direct URL to the message
;;;    :thread-id (optional) string can point to whole thread if it is possible
;;;    :id - unique id of message within its network
;;;    other keys as required...

;;; Mem storage provides simpliest in-memory implementation
;;; of SocialStorage just for development purposes
;;; Allows creation with pre-populated data
(defn mem-storage
  ([] (mem-storage {}))
  ([initial-data]
     (let [data (atom (merge {} initial-data))]
       (reify SocialStorage
         (get-credentials [_ network user-id]
           (get-in @data [:credentials network user-id]))
         (set-credentials [_ network user-id new-credentials]
           (swap! data update-in [:credentials network user-id] (constantly new-credentials)))
         (add-items [_ items]
           (let [update-fn (fn [old-items]
                             (concat old-items items))]
               (swap! data update-in [:items] update-fn)))
         (get-items [_]
           (:items @data))
         (get-items-for-user [_ user-id network]
           (->> (:items @data)
                (filter #(and (= network (:network-type %))
                              ((:recipients %) user-id)))))
         (get-user-posts [_ user-id network]
           (->> (:items @data)
                (filter #(and (= network (:network-type %))
                              (= user-id (:sender-id %))
                              (= :post (:message-type))))))
         (get-conversation-items [storage current-user-id companion-user-id network]
           (->> (:items @data)
                (filter #(and (= network (:network-type %))
                              (= (:from %) companion-user-id)
                              ((:recipients %) current-user-id)))))
         ;; (get-last-update [_ user-id network]
         ;;   (get-in @data [:updates network user-id]))
         ;; (set-last-update [_ user-id network timestamp]
         ;;   (swap! data update-in [:updates network user-id] (constantly timestamp)))
         ))))

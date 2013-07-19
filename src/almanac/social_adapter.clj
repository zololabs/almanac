(ns almanac.social-adapter
  (:require [almanac.storage :as ss]
            [almanac.core :as core]))

(defmulti update-activity
  (fn [network user-id system]
    network))

(defn update-activities [email networks system]
  "Gets new activity items for a user specified by email in networks
  System consists of :fullcontact-cache, :credentials-store, :activity-storage, :aux-cache
  Stores items in activity-storage
  Network adapter can also use aux-storage as a basic key-value store
  for storing any required caching information, for example, last
  update time or last available item and so on"
  (let [{:keys [activity-storage aux-cache fullcontact-cache]} system
         profiles (:socialProfiles (core/get-social-info email fullcontact-cache))]
    (mapcat #(update-activity % (% profiles) system) networks)))

;;; Social adapter allows gathering any activity info from social network
;;; and also
(defprotocol SocialAdapter
  ;; Stores to the system social storage all public activity of
  ;; user in network
  (update-user-activity [adapter user-id system])

  ;; Stores to the system social storage all activity related
  ;; to the user (his private messages, mentions and so on)
  (update-user-conversations [adapter user-id system])

  ;; Retrieves full conversation between user-id1 user-id2 with thread-id
  ;; might be helpful for showing context before reply
  (get-conversation [adapter user-id1 user-id2 thread-id system])

  ;; Sends a reply to the user-id on thread-id
  (reply-to-thread [adapter user-id thread-id message system]))

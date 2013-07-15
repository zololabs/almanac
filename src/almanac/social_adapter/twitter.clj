(ns almanac.social-adapter.twitter
  (:require [almanac.storage :as ss]
            [almanac.social-api.twitter :as twitter]
            [almanac.social-adapter :refer [update-activity]])
  (:import (java.text SimpleDateFormat)
           (java.util Date)))

(def ^:private twitter-date-format (SimpleDateFormat. "EEE MMM dd HH:mm:ss Z YYYY"))

(defn- twitter-date->date [date-str]
  (.parse twitter-date-format date-str))

(defn- mention->ActivityItem [status]
  {:id (:id_str status)
   :content (:text status)
   :network-type :twitter
   :message-type :mention
   :sender-id (get-in status [:user :id_str])
   :recipients (set (map :id_str (get-in status [:entities :user_mentions])))
   :thread-id (:in_reply_to_status_id_str status)
   :created-time (twitter-date->date (:created_at status))})

(defn- message->ActivityItem [message]
  {:id (:id_str message)
   :content (:text message)
   :network-type :twitter
   :message-type :message
   :sender-id (:sender_id_str message)
   :recipients (set (vector (:recipient_id_str message)))
   :created-time (twitter-date->date (:created_at message))})

(defmethod update-activity :twitter [network user-id storage]
  (let [creds (ss/get-credentials storage :twitter user-id)
        mentions (->> creds
                      (twitter/get-mentions)
                      (map mention->ActivityItem))
        messages (->> creds
                      (twitter/get-messages)
                      (map message->ActivityItem))]
    (ss/add-items storage mentions)
    (ss/add-items storage messages)
    (ss/set-last-update storage user-id :twitter (java.util.Date.))))

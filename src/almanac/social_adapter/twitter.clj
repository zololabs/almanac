(ns almanac.social-adapter.twitter
  (:require [almanac.storage :as ss]
            [almanac.cache :as kvstore]
            [twitter.oauth :as oauth]
            [twitter.api.restful :as api]
            [almanac.social-adapter :refer [update-activity SocialAdapter]])
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

(defn- tweet->ActivityItem [{:keys [id_str text created_at author_id_str]}]
  {:id id_str
   :content text
   :network-type :twitter
   :message-type :post
   :sender-id (get-in tweet [:user :id_str])
   :recepients nil
   :created-time (twitter-date->date created_at)})

(defn- oauth-creds-from [{:keys [app-consumer-key app-consumer-secret user-token token-secret]}]
  (oauth/make-oauth-creds app-consumer-key app-consumer-secret user-token token-secret))

(defn get-mentions [creds]
  (:body (api/statuses-mentions-timeline :params {:count 200} :oauth-creds (oauth-creds-from creds))))

(defn get-messages [creds]
  (:body (api/direct-messages :params {:count 200} :oauth-creds (oauth-creds-from creds))))

(defn- app-creds-from []
  (oauth/make-app-creds consumer-key consumer-secret))

(defn get-public-tweets [creds user-id]
  (:body (api/statuses-user-timeline :oauth-creds creds :params {:screen_name user-id :count 50})))


(defmethod update-activity :twitter [network user-id system]
  (let [{:keys [credentials-storage activity-storage aux-cache]} system
        creds (kvstore/get-credentials credentials-storage :twitter user-id)
        mentions (->> creds
                      (get-mentions)
                      (map mention->ActivityItem))
        messages (->> creds
                      (get-messages)
                      (map message->ActivityItem))]
    (ss/add-items activity-storage mentions)
    (ss/add-items activity-storage messages)
    (when aux-cache
      (kvstore/set-value aux-cache
                         (format "%s-%s-last-upd" user-id (name :twitter))
                         (java.util.Date.)))))

(defn adapter [consumer-key consumer-secret]
  (let [app-creds (oauth/make-app-creds consumer-key consumer-secret)]
    (reify SocialAdapter
      (update-user-activity [_ user-id system]
        (->> (get-public-tweets app-creds user-id)
             (map tweet->ActivityItem)
             (ss/add-items (:activity-storage system))))
      (update-user-conversations [_ user-id system]
        (update-activity :twitter user-id system))
      (get-conversation [_ user-id1 user-id2 thread-id system]
        nil)
      (reply-to-thread [adapter user-id thread-id message system]
        nil))))

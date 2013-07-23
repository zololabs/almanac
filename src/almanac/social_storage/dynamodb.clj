(ns almanac.social-storage.dynamodb
  (:require [rotary.client :as rotary]
            [almanac.storage :refer [SocialStorage]]))

(defn- user-key [user-id network]
  (str user-id (name network)))

(defn- sync-posts [creds posts]
  nil)

(defn- sync-mentions [creds mentions]
  nil)

(defn- sync-messages [creds messages]
  nil)

(defn- append-items [creds items]
  (let [by-type (group-by :message-type items)
        posts (:post by-type)
        mentions (:mention by-type)
        messages (:message by-type)]
    (sync-posts creds posts)
    (sync-mentions creds mentions)
    (sync-messages creds messages)))

(defn- get-posts [creds user-id network & {:keys [count offset] :or {:count 50}}]
  (rotary/query creds "Posts" (user-key user-id network) :limit count))

(defn dynamo-storage [access-key secret-key & {:keys [table-name-prefix]}]
  (let [creds {:access-key access-key
               :secret-key secret-key}]
    (reify
      SocialStorage
      (get-credentials [_ network user-id]
        nil)
      (set-credentials [_ network user-id new-credentials]
        nil)
      (add-items [_ items]
        (append-items creds items))
      (get-items-for-user [_ user-id network]
        nil)
      (get-user-posts [_ user-id network]
        (get-posts creds user-id network))
      (get-conversation-items [_ current-user-id companion-user-id network]
        nil)
      )))

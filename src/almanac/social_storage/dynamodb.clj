(ns almanac.social-storage.dynamodb
  (:require [clojure.set :as cset]
            [clojure.walk :refer [keywordize-keys]]
            [taoensso.faraday :as dynamo]
            [almanac.storage :refer [SocialStorage]]
            [almanac.system :refer [Service]])
  (:import [java.text SimpleDateFormat]))

(def ^:private dynamo-date-format (SimpleDateFormat. "YYYY.MM.dd HH:mm:ss Z"))

(defmulti serialized type)

(defmethod serialized java.lang.String [x]
  x)

(defmethod serialized clojure.lang.Keyword [x]
  (name x))

(defmethod serialized nil [x]
  "")

(defmethod serialized java.util.Date [x]
  (.format dynamo-date-format x))

(defn- ActivityItem->dynamo [x]
  (apply hash-map (apply concat
                         (filter #(not (empty? (second %)))
                                 (map (fn [[k v]]
                                        (vector (name k) (serialized v)))
                                      x)))))

(defn- dynamo->ActivityItem [x]
  (let [tmp (keywordize-keys x)
        tmp (update-in tmp [:created-time] #(.parse dynamo-date-format %))
        tmp (update-in tmp [:network-type] keyword)
        tmp (update-in tmp [:message-type] keyword)]
    tmp))

(defn- user-key [user-id network]
  (str user-id "@" (name network)))

(defn- prefixed-table [prefix table-name]
  (str prefix "-" table-name))

(defn- sync-in-table [creds table-name user-key new-items sync-attr]
  (let [sync-attr-name (name sync-attr)
        existing-item-ids (->> (dynamo/query creds table-name {"user-id" user-key})
                               (:items)
                               (map #(get % sync-attr-name)))
        new-item-ids (map #(get % sync-attr) new-items)
        new-ids (cset/difference (set new-item-ids) (set existing-item-ids))
        transform (fn [x]
                    )]
    (doseq [item new-items
            :when (contains? new-ids (get item sync-attr))]
      (dynamo/put-item creds table-name (ActivityItem->dynamo (assoc item :user-id user-key))))))

(defn- sync-posts [creds table-name posts]
  (let [by-user-network (group-by #(user-key (:sender-id %) (:network-type %))  posts)]
    (doseq [[ukey uposts] by-user-network]
      (sync-in-table creds table-name ukey uposts :id))))

(defn- append-items [creds prefix items]
  (let [by-type (group-by :message-type items)
        posts (:post by-type)
        mentions (:mention by-type)
        messages (:message by-type)]
    (sync-posts creds (prefixed-table prefix "posts") posts)))

(defn- get-posts [creds prefix user-id network]
  (->> (dynamo/query creds (prefixed-table prefix "posts") {"user-id" (user-key user-id network)})
       (:items)
       (map dynamo->ActivityItem)))

(def ^:private posts-table-def
  {:name "posts"
   :hash-key ["user-id" :s]
   :range-key ["id" :s]})

(def ^:private tables [posts-table-def])

(defn- ensure-table-exists [creds prefix {:keys [name hash-key range-key]}]
  (let [def-options {:block? true
                     :throughput {:read 1 :write 1}}
        options (if range-key
                  (assoc def-options :range-keydef range-key)
                  def-options)]
    (dynamo/ensure-table creds
                         (prefixed-table prefix "posts")
                         hash-key
                         options)))

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
        (append-items creds table-name-prefix items))
      (get-items-for-user [_ user-id network]
        nil)
      (get-user-posts [_ user-id network]
        (get-posts creds table-name-prefix  user-id network))
      (get-conversation-items [_ current-user-id companion-user-id network]
        nil)
      Service
      (start [_ options]
        (doseq [table-def tables]
          (ensure-table-exists creds table-name-prefix table-def)))
      (stop [_ options]
        (when (:delete-tables options)
          (doseq [{:keys [name]} tables]
            (dynamo/delete-table creds (prefixed-table table-name-prefix name)))))
      )))

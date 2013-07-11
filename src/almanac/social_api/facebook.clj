(ns almanac.social-api.facebook
  (:require [clojure.core.reducers :as r]
            [clojure.set :as cs]
            [clj-facebook-graph.auth :refer [with-facebook-auth]]
            [clj-facebook-graph.client :as client])
  (:import [java.util Date]
           [java.text SimpleDateFormat]))

;; TODO: find out what's wrong with built-in with-facebook-auth as it doesn't send
;; access token to the FB server
;; meanwhile a very dirty workaround with a token transferred through params
(defn api-call [credentials path & {:keys [params]}]
  (with-facebook-auth credentials
    (client/get path {:query-params (merge {:access_token (:access-token credentials)}
                                           params)})))

(defn get-mentions [credentials]
  "Returns mentions of user in statuses"
  (:data (:body (api-call credentials [:me :tagged]))))

(def ^:private fb-time-format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssZ"))

(defn- convert-fb-timestamp [text]
  (.parse fb-time-format text))

(defn- thread-update-time [{:keys [updated_time]}]
  (convert-fb-timestamp updated_time))

;; Facebook message is a map with:
;;    id = message id
;;    thread-id = id of parent thread
;;    text = text of message
;;    from = user-id of sender
;;    to = set of all recepients
;;    created-time = time of creation (available only for comments), heuristics for start msg

;; Thread has 2 different things - the starting message in the root of response
;; and all other marked as comments/data

(defn- time-for-start-msg [thread comments]
  (if (empty? comments)
    (convert-fb-timestamp (:updated_time thread))
    (:created-time (first comments))))

(defn- start-msg->fb-msg [user-id thread comments recipients]
  (let [from (or (get-in thread [:from :id])
                 user-id)]
    {:text (:message thread)
     :id (:id thread)
     :thread-id (:id thread)
     :from from
     :to (cs/difference recipients (set (vector from)))
     :created-time (time-for-start-msg thread comments)}))

(defn- thread-comment->fb-msg [thread comment recipients]
  (let [from (or (get-in thread [:from :id]))]
    {:text (:message comment)
     :thread-id (:id thread)
     :created-time (convert-fb-timestamp (:created_time comment))
     :from from
     :id (:id comment)
     :to (cs/difference recipients (set (vector from)))}))

;; TODO: start message might require user id, in case conversation was started
;; by user, so it should be also passed here
(defn get-thread-messages [credentials thread-id filters]
  "Returns a sequence of private messages in thread-id
  filters should be a map with possible :message-filter-fn for
  filtering individual messages by Date"
  (let [thread (:body (api-call credentials [(format "%s" thread-id)]))
        recipients (set (map :id (:data (:to thread))))
        time-filter (or (:message-filter-fn filters)
                        (constantly true))
        comments (->> (get-in thread [:comments :data])
                      (r/map #(thread-comment->fb-msg thread % recipients))
                      (r/filter #(time-filter (:created-time %)))
                      (into []))
        start-msg (start-msg->fb-msg "user-id-goes-here" thread comments recipients)]
    (cons start-msg comments)))

(defn get-messages [credentials & {:keys [filters]}]
  "Returns a sequence of private messages to/from user
  filters should be a map with :thread-filter-fn for filtering threads
  and :message-filter-fn for filtering individual messages by Date"
  (let [time-filter (or (:thread-filter-fn filters)
                        (constantly true))]
    (->> (api-call credentials [:me :inbox] :params {:fields "updated_time"})
         :body
         :data
         (r/filter #(time-filter (thread-update-time %)))
         (r/map #(get-thread-messages credentials (:id %) filters))
         (reduce concat))))

(defn get-photo-mentions [credentials]
  "Returns photos where current user is tagged"
  (:data (:body (api-call credentials [:me :photos]))))

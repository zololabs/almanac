(ns almanac.social-adapter.facebook
  (:require [almanac.storage :as ss]
            [almanac.cache :as kvstore]
            [almanac.social-adapter :refer [update-activity SocialAdapter]]
            [almanac.social-api.facebook :as fb])
  (:import [java.util Date]))

(defn- fb-message->ActivityItem [msg]
  {:network-type :facebook
   :recipients (:to msg)
   :sender-id (:from msg)
   :message-type :message
   :link (format "https://graph.facebook.com/%s" (:id msg))
   :thread-id (:thread-id msg)
   :id (:id msg)
   :content (:text msg)
   :created-time (:created-time msg)})

(defn- fb-status->ActivityItem [status]
  nil)

(defn- fb-photo->ActivityItem [photo]
  nil)

;; TODO: move out those functions somewhere to the utils?
(defn- date-diff-in-seconds [ref-date date]
  "Difference (in seconds) between ref-date and date. Negative means date is earlier than ref-date"
  (- (.getTime ref-date) (.getTime date)))

(defn- gen-date-diff-predicate [ref-date sec-interval]
  "Constructs a predicate which is true for all dates close than sec-interval to ref-date"
  (fn [date]
    (< (date-diff-in-seconds ref-date date) sec-interval)))

(def ^:private MSECS-IN-MONTH (* 1000 60 60 24 31))

(def ^:private recent-enough? (gen-date-diff-predicate (Date.) MSECS-IN-MONTH))
;; end of section to be moved

(defmethod update-activity :facebook [network user-id system]
  (let [{:keys [activity-storage credentials-storage]} system
        creds (kvstore/get-credentials credentials-storage :facebook user-id)]
    (doseq [[get-fn convert-fn] [[#(fb/get-messages % :filters {:thread-filter-fn recent-enough?}) fb-message->ActivityItem]
                                 [fb/get-mentions fb-status->ActivityItem]
                                 [fb/get-photo-mentions fb-photo->ActivityItem]]]
      (->> (get-fn creds)
           (map convert-fn)
           (ss/add-items activity-storage)))))

(defn- update-user-activity [api-id app-secret user-id system]
  (let [{:keys [activity-storage credentials-storage]} system
        creds (kvstore/get-credentials credentials-storage :facebook user-id)]
    (->> (fb/get-user-activity creds user-id)
         (map fb-status->ActivityItem)
         (ss/add-items activity-storage))))

(defn adapter [app-id app-secret]
  (reify SocialAdapter
    (update-user-activity [_ user-id system]
      (update-user-activity app-id app-secret user-id system))
    (update-user-conversations [_ user-id system]
      (update-activity :facebook user-id system)) ;; for now just to call existing function
    (get-conversation [_ user-id1 user-id2 thread-id system])
    (reply-to-thread [_ user-id thread-id message system])))

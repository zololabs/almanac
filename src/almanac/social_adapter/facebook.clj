(ns almanac.social-adapter.facebook
  (:require [almanac.storage :as ss]
            [almanac.social-adapter :refer [update-activity]]
            [almanac.social-api.facebook :as fb]))

(defn- fb-message->ActivityItem [msg]
  nil)

(defn- fb-status->ActivityItem [status]
  nil)

(defn- fb-photo->ActivityItem [photo]
  nil)

(defmethod update-activity :facebook [network user-id storage]
  (let [creds (ss/get-credentials storage :facebook user-id)]
    (doseq [[get-fn convert-fn] [[fb/get-messages fb-message->ActivityItem]
                                 [fb/get-mentions fb-status->ActivityItem]
                                 [fb/get-photo-mentions fb-photo->ActivityItem]]]
      (->> (get-fn creds)
           (map convert-fn)
           (ss/add-items storage user-id :facebook)))))

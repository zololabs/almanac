(ns almanac.social-adapter.twitter
  (:require [almanac.storage :as ss]
            [almanac.social-api.twitter :as twitter]))

(defn- twitter->ActivityItem [twitter-item]
  nil)

(defmethod update-activity :twitter [network user-id storage]
  (let [items (->> user-id
                   (ss/get-credentials storage :twitter)
                   (twitter/get-mentions)
                   (map twitter->ActivityItem))]
    (ss/add-items storage user-id :twitter items)
    (ss/set-last-update storage user-id :twitter (Date.))))

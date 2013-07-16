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

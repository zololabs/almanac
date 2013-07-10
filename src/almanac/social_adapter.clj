(ns almanac.social-adapter
  (:require [almanac.storage :as s :refer [get-profiles-by-email]]))

(defmulti update-activity
  (fn [network user-id storage]
    network))

(defn update-activities [storage email networks]
 (let [profiles (:socialProfiles (get-profiles-by-email storage email))]
   (mapcat #(update-activity % (:% profiles) storage) networks)))

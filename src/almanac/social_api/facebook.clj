(ns almanac.social-api.facebook
  (:require [clj-facebook-graph.auth :refer [with-facebook-auth]]
            [clj-facebook-graph.client :as client]]))

(defn get-mentions [credentials]
  (with-facebook-auth credentials
    (client/get [:me :tagged])))

(defn get-messages [credentials]
  (with-facebook-auth credentials
    (client/get [:me :inbox])))

(defn get-photo-mentions [credentials]
  (with-facebook-auth credentials
    (client/get [:me :photos])))

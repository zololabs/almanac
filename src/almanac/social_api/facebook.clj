(ns almanac.social-api.facebook
  (:require [clj-facebook-graph.auth :refer [with-facebook-auth]]
            [clj-facebook-graph.client :as client]))

;; TODO: find out what's wrong with built-in with-facebook-auth as it doesn't send
;; access token to the FB server
;; meanwhile a very dirty workaround with a token transferred through params
(defn- api-call [credentials path]
  (with-facebook-auth credentials
    (client/get path {:query-params {:access_token (:access-token credentials)}
                      :extract :data})))

(defn get-mentions [credentials]
  (api-call credentials [:me :tagged]))

(defn get-messages [credentials]
  (api-call credentials [:me :inbox]))

(defn get-photo-mentions [credentials]
  (api-call credentials [:me :photos]))

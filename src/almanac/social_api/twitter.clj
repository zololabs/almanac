(ns almanac.social-api.twitter
  (:require [twitter.oauth :as oauth]
            [twitter.api.restful :as api]))

(defn get-mentions [{:keys [app-consumer-key app-consumer-secret user-token user-secret]}]
  (api/statuses-mentions-timeline :oauth-creds (oauth/make-oauth-creds app-consumer-key app-consumer-secret user-token user-secret)))

(defn get-messages [{:keys [app-consumer-key app-consumer-secret user-token user-secret]}]
  (api/direct-messages-show :oauth-creds (oauth/make-oauth-creds app-consumer-key app-consumer-secret user-token user-secret)))

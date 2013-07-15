(ns almanac.social-api.twitter
  (:require [twitter.oauth :as oauth]
            [twitter.api.restful :as api]))

(defn- oauth-creds-from [{:keys [app-consumer-key app-consumer-secret user-token token-secret]}]
  (oauth/make-oauth-creds app-consumer-key app-consumer-secret user-token token-secret))

(defn get-mentions [creds]
  (:body (api/statuses-mentions-timeline :params {:count 200} :oauth-creds (oauth-creds-from creds))))

(defn get-messages [creds]
  (:body (api/direct-messages :params {:count 200} :oauth-creds (oauth-creds-from creds))))

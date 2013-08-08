(ns almanac.cache.dynamodb
  (:require [almanac.cache :refer [KeyValueStore]])
  (:require [almanac.system :refer [Service]])
  (:require [taoensso.faraday :as dynamo])
  (:require [cheshire.core :as json]))

(defn- get-value [aws-creds table-name key]
  (->> (dynamo/get-item aws-creds table-name {:id key})
       (:data)))

(defn- set-value [aws-creds table-name key value]
  (dynamo/put-item aws-creds table-name {:id key
                                         :version 2
                                         :mime-type "application/x-nippy"
                                         :data (dynamo/freeze value)}))

;; TODO: throughput should be modifiable
(defn- ensure-table-exists [aws-creds table-name async]
  (dynamo/ensure-table aws-creds table-name ["id" :s]
                       {:throughput {:read 1 :write 1}
                        :block? (not async)}))

(defn cache [access-key secret-key table-name]
  (let [aws-creds {:access-key access-key
                   :secret-key secret-key}
        table-name (keyword table-name)]
   (reify
     KeyValueStore
     (get-value [_ key]
       (get-value aws-creds table-name key))
     (set-value [_ key value]
       (set-value aws-creds table-name key value))
     Service
     (start [this options]
       (ensure-table-exists aws-creds table-name (:async options)))
     (stop [_]))))

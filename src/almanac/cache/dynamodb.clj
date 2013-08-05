(ns almanac.cache.dynamodb
  (:require [almanac.cache :refer [KeyValueStore]])
  (:require [almanac.system :refer [Service]])
  (:require [rotary.client :as dynamo])
  (:require [cheshire.core :as json]))

(defn- encode [value]
  {:version 1
   :mime-type "application/json"
   :data (json/generate-string value)})

(defn- decode [{:strs [version mime-type data] :as value}]
  (when (and (= version 1)
             (= mime-type "application/json"))
    (json/parse-string data true)))

(defn- get-value [aws-creds table-name key]
  (->> (assoc {} :id key)
       (dynamo/get-item aws-creds table-name)
       (decode)))

(defn- set-value [aws-creds table-name key value]
  (let [encoded-value (-> value
                          (encode)
                          (assoc :id key))]
    (dynamo/put-item aws-creds table-name encoded-value)))

(defn- active-table? [aws-creds table-name]
  (->> table-name
       (dynamo/describe-table aws-creds)
       (:status)
       (= :active)))

;; TODO: throughput should be modifiable
(defn- ensure-table-exists [aws-creds table-name async]
  (let [properties {:name table-name
                    :hash-key {:name "id"
                               :type :s}
                    :throughput {:read 1
                                 :write 1}}]
      (dynamo/ensure-table aws-creds properties)
      (if (not async)
        (drop-while identity
                    (repeatedly #(do
                                   (Thread/sleep 1000)
                                   (not (active-table? aws-creds table-name))))))))

(defn cache [access-key secret-key table-name]
  (let [aws-creds {:access-key access-key
                   :secret-key secret-key}]
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

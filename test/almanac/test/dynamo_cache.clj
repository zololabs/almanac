(ns almanac.test.dynamo-cache
  (:require [almanac.cache.dynamodb :as dynamo])
  (:require [almanac.cache :as cache])
  (:require [almanac.system :as system])
  (:require [environ.core :refer [env]])
  (:use [clojure.test]))

(deftest dynamo-cache
  (testing "Basic test"
    (let [{:keys [aws-access-key aws-secret-key]} env
          dc (dynamo/cache aws-access-key aws-secret-key "test-cache")
          test-info {:services ["twitter"]
                     :socialProfiles
                     {"twitter" [{:username "t" :url "u"}]}
                     :photos
                     {"twitter" [{:url "pu"}]}}
          test-email "me@somewhere"]
      (system/start dc {:async false})
      (is (nil? (cache/get-value dc test-email)))
      (cache/set-value dc test-email test-info)
      (is (= (cache/get-value dc test-email)
             test-info)))))

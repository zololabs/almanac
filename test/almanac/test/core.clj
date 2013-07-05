(ns almanac.test.core
  (:use [clojure.test])
  (:require [almanac.core :as core]
            [almanac.cache :as cache]
            [almanac.fullcontact :as fullcontact]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [slingshot.slingshot :refer [throw+]]))

(deftest test-core
  (testing "Map fullcontact to our"
    (let [fc-map-test-data (slurp (io/resource "fc-map.txt"))
          resp (json/parse-string fc-map-test-data true)
          fc-map-test-result (edn/read-string (slurp (io/resource "fc-map-correct.edn")))]
      (is (= (core/fullcontact->almanac resp)
             fc-map-test-result))))

  (testing "Cache work"
    (let [info (->> "fc-map-correct.edn"
                    io/resource
                    slurp
                    edn/read-string)
          test-email "t@t.com"
          cache (cache/mem-store)]
      (with-redefs [fullcontact/find-person (fn [email]
                                              (throw+ {:error "Fullcontact shouldn't be called at all!"}))]
        (cache/set-value cache test-email info)
        (is (= (core/get-social-info test-email cache)
               info))))))

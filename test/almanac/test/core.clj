(ns almanac.test.core
  (:use [clojure.test])
  (:require [almanac.core :as core]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.edn :as edn]))

(deftest test-core
  (testing "Map fullcontact to our"
    (let [fc-map-test-data (slurp (io/resource "fc-map.txt"))
          resp (json/parse-string fc-map-test-data true)
          fc-map-test-result (edn/read-string (slurp (io/resource "fc-map-correct.edn")))]
      (is (= (core/fullcontact->almanac resp)
             fc-map-test-result)))))

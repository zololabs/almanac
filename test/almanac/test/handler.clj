(ns almanac.test.handler
  (:use clojure.test
        ring.mock.request
        almanac.handler)
  (:require [almanac.core :as core]
            [almanac.utils :as utils]
            [cheshire.core :as json]))

(deftest test-app
  (testing "Invalid email address"
    (let [{:keys [body status]} (app (request :get "/api/person" {:email "first.last@sub.do,com"}))]
      (is (= status 400))
      (is (= body (json/generate-string {:error "Invalid email address"})))))

  (testing "No info"
    (with-redefs [core/get-social-info (fn [& args] nil)]
      (let [{:keys [body status]} (app (request :get "/api/person" {:email "t@t.com"}))]
        (is (= status 404))
        (is (empty? body)))))

  (testing "Internal error"
    (with-redefs [core/get-social-info (fn [& args] (throw (Exception. "test")))]
      (let [{:keys [body status]} (app (request :get "/api/person" {:email "t@t.com"}))]
        (is (= status 500))
        (is (= body (json/generate-string {:error "exception info: java.lang.Exception: test"}))))))

  (testing "Valid response"
    (with-redefs [core/get-social-info (fn [&args] {:a 1 :b 2})]
      (let [{:keys [body status]} (app (request :get "/api/person" {:email "t@t.com"}))]
        (is (= status 200))
        (is (= body (json/generate-string {:a 1 :b 2})))))))

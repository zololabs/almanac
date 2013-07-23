(defproject almanac "0.1.0-SNAPSHOT"
  :description "Almanac service for Zolo Labs"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/java.jdbc "0.2.3"]
                 [org.clojure/tools.logging "0.2.6"]
                 [compojure "1.1.5"]
                 [environ "0.4.0"]
                 [slingshot "0.10.3"]
                 [cheshire "3.1.0"]
                 [clj-http "0.7.2"]
                 [lobos "1.0.0-beta1"]
                 [korma "0.3.0-RC5"]
                 [rotary "0.4.0"]
                 [twitter-api "0.7.4"]
                 [clj-facebook-graph "0.4.1-SNAPSHOT"]
                 [mysql/mysql-connector-java "5.1.25"]
                 [zololabs/zolo-utils "0.1.0-SNAPSHOT"]
                 [ch.qos.logback/logback-classic "1.0.13"]]
  :plugins [[lein-ring "0.8.5"]]
  :ring {:handler almanac.handler/app}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]
                        [org.clojure/tools.namespace "0.2.3"]]
         :source-paths ["dev"]
         :resource-paths ["dev-resources"]
         :ring {:handler almanac.handler/dev-app}}})

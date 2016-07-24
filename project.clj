(defproject iapetos "0.1.0-SNAPSHOT"
  :description "A Clojure Prometheus Client"
  :url "https://github.com/xsc/iapetos"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2016
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [io.prometheus/simpleclient "0.0.15"]
                 [io.prometheus/simpleclient_common "0.0.15"]
                 [io.prometheus/simpleclient_pushgateway "0.0.15"]
                 [io.prometheus/simpleclient_hotspot "0.0.15" :scope "provided"]]
  :pedantic? :abort)

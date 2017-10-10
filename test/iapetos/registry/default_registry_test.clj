(ns iapetos.registry.default_registry_test
  (:require [iapetos.registry :as registry]
            [iapetos.core :as prometheus]
            [iapetos.collector.fn :as fn]
            [clojure.test :refer [deftest is testing]])
  (:import [io.prometheus.client CollectorRegistry]))

(defn init-default-registry []
  (do
    (-> registry/default
        ^CollectorRegistry (registry/raw)
        (.clear))
    (-> registry/default
        ;; ...other initializers or registrations
        (fn/initialize))))

(defn test-fn []
  "EHLO")

(deftest default-registry
  (is (some? (registry/raw registry/default)))

  (testing "default registry can be initialized a first time"
    (let [default-registry (init-default-registry)]
      (is (some? default-registry))
      (fn/instrument! default-registry #'test-fn)
      (is (string? (test-fn)))))

  (testing "default registry can be initialized a second time"
    (let [default-registry (init-default-registry)]
      (is (some? default-registry))
      (is (some? (prometheus/value default-registry :fn/duration-seconds)))
      (fn/instrument! default-registry #'test-fn {:fn-name "test-fn"})
      (is (string? (test-fn)))
      (is (= 1.0 (:count (prometheus/value default-registry :fn/duration-seconds {:fn "test-fn"}))))))
  )

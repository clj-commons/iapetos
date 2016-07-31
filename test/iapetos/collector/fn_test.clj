(ns iapetos.collector.fn-test
  (:require [clojure.test :refer :all]
            [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]
            [iapetos.collector.fn :as fn]))

;; ## Generators

(def gen-fn-registry
  (gen/let [registry-name g/metric-string]
    (gen/return
      (-> (prometheus/collector-registry)
          (fn/initialize)))))

;; ## Tests

(defspec t-wrap-instrumentation 10
  (prop/for-all
    [registry gen-fn-registry
     [type f] (gen/elements
                [[:success #(Thread/sleep 20)]
                 [:failure #(do (Thread/sleep 20) (throw (Exception.)))]])]
    (let [f' (fn/wrap-instrumentation f registry "f" {})
          start-time (System/currentTimeMillis)
          start (System/nanoTime)
          _  (dotimes [_ 5] (try (f') (catch Throwable _)))
          end-time (System/currentTimeMillis)
          delta (/ (- (System/nanoTime) start) 1e9)
          val-of #(prometheus/value registry %1 (into {:fn "f"} %2))]
      (and (<= 0.1 (:sum (val-of :fn/duration-seconds {})) delta)
           (= 5.0 (:count (val-of :fn/duration-seconds {})))
           (or (= type :success)
               (= 5.0 (val-of :fn/exceptions-total {:exceptionClass "java.lang.Exception"})))
           (or (= type :failure)
               (= 5.0 (val-of :fn/runs-total {:result "success"})))
           (or (= type :success)
               (= 5.0 (val-of :fn/runs-total {:result "failure"})))
           (or (= type :success)
               (<= (- end-time 20)
                   (* 1000 (val-of :fn/last-failure-unixtime {}))
                   end-time))))))

(def test-fn nil)

(defn- reset-test-fn!
  [f]
  (alter-var-root #'test-fn (constantly f))
  (alter-meta! #'test-fn (constantly {})))

(defspec t-instrument! 10
  (prop/for-all
    [registry gen-fn-registry
     [type f] (gen/elements
                [[:success #(Thread/sleep 20)]
                 [:failure #(do (Thread/sleep 20) (throw (Exception.)))]])]
    (reset-test-fn! f)
    (fn/instrument! registry #'test-fn)
    (let [start-time (System/currentTimeMillis)
          start (System/nanoTime)
          _  (dotimes [_ 5] (try (test-fn) (catch Throwable _)))
          end-time (System/currentTimeMillis)
          delta (/ (- (System/nanoTime) start) 1e9)
          val-of #(prometheus/value
                    registry
                    %1
                    (into {:fn "iapetos.collector.fn-test/test-fn"} %2))]
      (and (<= 0.1 (:sum (val-of :fn/duration-seconds {})) delta)
           (= 5.0 (:count (val-of :fn/duration-seconds {})))
           (or (= type :success)
               (= 5.0 (val-of :fn/exceptions-total {:exceptionClass "java.lang.Exception"})))
           (or (= type :failure)
               (= 5.0 (val-of :fn/runs-total {:result "success"})))
           (or (= type :success)
               (= 5.0 (val-of :fn/runs-total {:result "failure"})))
           (or (= type :success)
               (<= (- end-time 20)
                   (* 1000 (val-of :fn/last-failure-unixtime {}))
                   end-time))))))

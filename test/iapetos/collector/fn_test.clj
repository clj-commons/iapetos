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

(defn gen-fn-registry [label-keys]
  (g/registry-fn #(fn/initialize %1 {:labels label-keys})))

(def gen-labels
  (gen/not-empty
    (gen/map
    g/metric-string
    g/metric-string)))

;; ## Tests

(defspec t-wrap-instrumentation 10
  (prop/for-all
    [[labels registry-fn]
     (gen/let [labels gen-labels
               registry-fn (gen-fn-registry (keys labels))]
       [labels registry-fn])
     [type f]   (gen/elements
                  [[:success #(Thread/sleep 20)]
                   [:failure #(do (Thread/sleep 20) (throw (Exception.)))]])]
    (let [registry (registry-fn)
          f' (fn/wrap-instrumentation f registry "f" {:labels labels})
          start-time (System/currentTimeMillis)
          start (System/nanoTime)
          _  (dotimes [_ 5] (try (f') (catch Throwable _)))
          end-time (System/currentTimeMillis)
          delta (/ (- (System/nanoTime) start) 1e9)
          val-of #(prometheus/value registry %1 (into {} (list {:fn "f"} labels %2)))]
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
    [[labels registry-fn]
     (gen/let [labels gen-labels
               registry-fn (gen-fn-registry (keys labels))]
       [labels registry-fn])
     [type f] (gen/elements
               [[:success #(Thread/sleep 20)]
                [:failure #(do (Thread/sleep 20) (throw (Exception.)))]])]
    (reset-test-fn! f)
    (let [registry (doto (registry-fn)
                     (fn/instrument! #'test-fn {:labels labels}))
          start-time (System/currentTimeMillis)
          start (System/nanoTime)
          fn-name "iapetos.collector.fn-test/test-fn"
          _  (dotimes [_ 5] (try (test-fn) (catch Throwable _)))
          end-time (System/currentTimeMillis)
          delta (/ (- (System/nanoTime) start) 1e9)
          val-of #(prometheus/value registry %1 (into {} (list {:fn fn-name} labels %2)))]
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

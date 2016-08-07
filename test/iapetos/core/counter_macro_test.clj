(ns iapetos.core.counter-macro-test
  (:require [clojure.test :refer :all]
            [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]))

;; ## Helpers

(defmacro run-success!
  [n f counter]
  `(dotimes [_# ~n]
    (~f ~counter
      (comment 'do-something))))

(defmacro run-failure!
  [n f counter]
  `(dotimes [_# ~n]
     (try
       (~f ~counter
           (throw (Exception.)))
       (catch Throwable _#))))

(defmacro run-test!
  ([n f counter]
   `(run-test! ~n ~n ~f ~counter))
  ([n m f counter]
   `(do
      (run-success! ~n ~f ~counter)
      (run-failure! ~m ~f ~counter))))

;; ## Generator

(def gen-countable
  (gen/let [metric g/metric
            countable (gen/elements
                        [(prometheus/counter metric)
                         (prometheus/gauge   metric)])
            registry-fn (g/registry-fn)]
    (let [registry (-> (registry-fn)
                       (prometheus/register countable))]
      (gen/return (registry metric)))))

;; ## Tests

(defspec t-with-counter 100
  (prop/for-all
    [counter gen-countable]
    (run-test! 5 prometheus/with-counter counter)
    (= 10.0 (prometheus/value counter))))

(defspec t-with-success-counter 100
  (prop/for-all
    [counter gen-countable]
    (run-test! 7 3 prometheus/with-success-counter counter)
    (= 7.0 (prometheus/value counter))))

(defspec t-with-failure-counter 100
  (prop/for-all
    [counter gen-countable]
    (run-test! 7 3 prometheus/with-failure-counter counter)
    (= 3.0 (prometheus/value counter))))

(defspec t-with-counters
  (prop/for-all
    [total-counter   gen-countable
     success-counter gen-countable
     failure-counter gen-countable]
    (dotimes [_ 7]
      (prometheus/with-counters
        {:success success-counter
         :failure failure-counter
         :total   total-counter}
        (comment 'do-something)))
    (dotimes [_ 3]
      (try
        (prometheus/with-counters
          {:success success-counter
           :failure failure-counter
           :total   total-counter}
          (throw (Exception.)))
        (catch Throwable _)))
    (and (= 10.0 (prometheus/value total-counter))
         (= 7.0 (prometheus/value success-counter))
         (= 3.0 (prometheus/value failure-counter)))))

(defspec t-with-activity-counter 5
  (prop/for-all
    [registry-fn (g/registry-fn)]
    (let [metric :app/activity-total
          registry (-> (registry-fn)
                       (prometheus/register
                         (prometheus/gauge metric)))
          counter (registry metric)
          start-promise (promise)
          started-promise (promise)
          finish-promise (promise)
          job (future
                @start-promise
                (prometheus/with-activity-counter counter
                  (deliver started-promise true)
                  @finish-promise))]
      (is (= 0.0 (prometheus/value counter)))
      (deliver start-promise true)
      @started-promise
      (is (= 1.0 (prometheus/value counter)))
      (deliver finish-promise true)
      @job
      (is (= 0.0 (prometheus/value counter))))))

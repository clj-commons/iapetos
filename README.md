# iapetos

__iapetos__ is a Clojure wrapper around the [Prometheus Java
Client][java-client].

[java-client]: https://github.com/prometheus/client_java

## Usage

Don't. This is mostly me exploring Prometheus.

## Basic Usage

### Registering Metrics

[__Documentation__](https://xsc.github.io/iapetos/iapetos.core.html)

All metrics have to be registered with a collector registry before being used:

```clojure
(require '[iapetos.core :as prometheus])

(defonce registry
  (-> (prometheus/collector-registry)
      (prometheus/register
        (prometheus/histogram :app/duration-seconds)
        (prometheus/gauge     :app/last-success-unixtime {:lazy? true})
        (prometheus/gauge     :app/active-users-total    {:lazy? true})
        (prometheus/counter   :app/runs-total))))
```

Now, they are ready to be set and changed:

```clojure
(-> registry
    (prometheus/inc     :app/runs-total)
    (prometheus/observe :app/duration-seconds 0.7)
    (prometheus/set     :app/active-users-total 22))
```

The registry itself implements `clojure.lang.IFn` to allow access to all
registered metrics (plus setting of metric [labels](#labels)), e.g.:

```clojure
(registry :app/duration-seconds)
;; => #object[io.prometheus.client.Histogram$Child ...]
```

All metric operations can be called directly on such a collector, i.e.:

```clojure
(prometheus/inc     (registry :app/runs-total))
(prometheus/observe (registry :app/duration-seconds) 0.7)
(prometheus/set     (registry :app/active-users-total) 22)
```

### Metric Export

[__Documentation__](https://xsc.github.io/iapetos/iapetos.export.html)

Metrics can be transformed into a textual representation using
`iapetos.export/text-format`:

```clojure
(require '[iapetos.export :as export])

(print (export/text-format registry))
;; # HELP app_active_users_total a gauge metric.
;; # TYPE app_active_users_total gauge
;; app_active_users_total 22.0
;; # HELP app_runs_total a counter metric.
;; # TYPE app_runs_total counter
;; app_runs_total 1.0
;; ...
```

This could now be exposed e.g. using an HTTP endpoint (see also iapetos'
[Ring](#ring) integration). Alternatively, metrics can be pushed to the
respective Prometheus `PushGateway` if applicable:

```clojure
(export/push! registry {:gateway "push-gateway:12345"})
```

### Labels

Prometheus allows for labels to be associated with metrics which can be declared
for each collector before it is registered:

```clojure
(def job-latency-histogram
  (prometheus/histogram
    :app/job-latency-seconds
    {:description "job execution latency by job type"
     :labels [:job-type]
     :buckets [1.0 5.0 7.5 10.0 12.5 15.0]}))

(defonce registry
  (-> (prometheus/collector-registry)
      (prometheus/register job-latency-histogram)))
```

Now, you can lookup a collector bound to a set of labels by calling the
registry with a label/value-map:

```clojure
(prometheus/observe (registry :app/job-latency-seconds {:job-type "pull"}) 14.2)
(prometheus/observe (registry :app/job-latency-seconds {:job-type "push"}) 8.7)

(print (export/text-format registry))
;; # HELP app_job_latency_seconds job execution latency by job type
;; # TYPE app_job_latency_seconds histogram
;; app_job_latency_seconds_bucket{job_type="pull",le="1.0",} 0.0
;; app_job_latency_seconds_bucket{job_type="pull",le="5.0",} 0.0
;; ...
;; app_job_latency_seconds_bucket{job_type="push",le="1.0",} 0.0
;; app_job_latency_seconds_bucket{job_type="push",le="5.0",} 0.0
;; ...
```

## Features

### JVM Metrics

[__Documentation__](https://xsc.github.io/iapetos/iapetos.collector.jvm.html)

Some characteristics of your current JVM are always useful (e.g. memory
usage, thread count, ...) and can be added to your registry using the
`iapetos.collector.jvm` namespace:

```clojure
(require '[iapetos.collector.jvm :as jvm])

(defonce registry
  (-> (prometheus/collector-registry)
      (prometheus/register
        ...
        (jvm/standard)
        (jvm/gc)
        (jvm/memory-pools)
        (jvm/threads))))
```

There is also `iapetos.collector.jvm/all` which will register all JVM
collectors.

__Note:__ You need to include the artifact `io.prometheus/simpleclient_hotspot`
explicitly in your project's dependencies.

### Function Instrumentation

[__Documentation__](https://xsc.github.io/iapetos/iapetos.collector.fn.html)

To collect metrics about specific functions, you can use the functionality
provided in `iapetos.collector.fn`:

```clojure
(require '[iapetos.collector.fn :as fn])

(defn- run-the-job!
  [job]
  ...)

(defonce registry
  (-> (prometheus/collector-registry)
      ...
      (fn/initialize)
      (fn/instrument #'run-the-job!)))
```

Now, every call to `run-the-job!` will update a series of duration, success and
failure metrics. Note, however, that re-evaluation of the `run-the-job!`
declaration will remove the instrumentation again.

### Ring

[__Documentation__](https://xsc.github.io/iapetos/iapetos.collector.ring.html)

`iapetos.collector.ring` offers middlewares to

- expose a iapetos collector registry via a fixed HTTP endpoint, and
- collect metrics for Ring handlers.

First, you need to initialize the available collectors in the registry:

```clojure
(require '[iapetos.collector.ring :as ring])

(defonce registry
  (-> (prometheus/collector-registry)
      (ring/initialize)))
```

Afterwards, you can add the middlewares to your Ring stack:

```clojure
(def app
  (-> (constantly {:status 200})
      (ring/wrap-instrumentation registry)
      (ring/wrap-metrics registry {:metrics-uri "/metrics"})))
```

The following metrics will now be collected and exposed via the `/metrics`
endpoint:

- `http_requests_total`
- `http_request_latency_seconds`

These are, purposefully, compatible with the metrics produced by
[prometheus-clj](https://github.com/soundcloud/prometheus-clj), as to allow a
smooth migration.

## License

```
MIT License

Copyright (c) 2016 Yannick Scherer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

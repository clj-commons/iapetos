# iapetos

__iapetos__ is a Clojure wrapper around the [Prometheus Java
Client][java-client].

[java-client]: https://github.com/prometheus/client_java

## Usage

Don't. This is mostly me exploring Prometheus.

### Basic Usage

Metrics have to be declared on a collector registry before being used:

```clojure
(require '[iapetos.core :as prometheus]
         '[iapetos.hotspot :as hotspot])

(defonce registry
  (-> (prometheus/collector-registry "my_application")
      (prometheus/histogram "duration_seconds")
      (prometheus/gauge     "last_success_unixtime" {:lazy? true})
      (prometheus/gauge     "active_users_total"    {:lazy? true})
      (prometheus/counter   "runs_total")))
```

Now, you can write an instrumented function using some of iapetos' helper macros
or by operating directly on the collectors:

```clojure
(defn run
  []
  (prometheus/inc registry "runs_total")
  (prometheus/with-duration-histogram [registry "duration_seconds"]
    (prometheus/with-success-timestamp [registry "last_success_unixtime"]
      ...
      (prometheus/set registry "active_users_total" (count-users!)))))
```

The metrics can then be either exported using a textual representation:

```clojure
(require '[iapetos.export :as export])
(print (export/text-format registry))
;; # HELP my_application_duration_seconds a histogram
;; # TYPE my_application_duration_seconds histogram
;; my_application_duration_seconds_bucket{le="0.005",} 0.0
;; my_application_duration_seconds_bucket{le="0.01",} 0.0
;; my_application_duration_seconds_bucket{le="0.025",} 0.0
;; ...
```

### More

Soon.

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

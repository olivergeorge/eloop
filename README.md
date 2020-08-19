I scratched an itch and tried to rewrite the re-frame event loop.  Here's what I came up with...

Two novel properties:

* It’s designed to encourage composable logic.  Logic handlers take state and return state.  This allows :db to flow through handlers.  For side effects you can hook up an :fx key which is a list of effects to process.

* It’s compatible with asynchronous data source inputs.  The new world order of asynchronous apis is turning our “pure logic” handlers into callback hell.  This should allow SQLite queries as inputs to handlers on React Native apps.  Only good for reference data (not “state”) and APIs which won’t block or be slow to respond.

There are [examples](./examples/example/) in the repo.

NOTE: I'm confident composable logic is a good idea.  Asynchronous inputs, on the other hand, are unproven.

# Types of handlers

The event loop can be customised by registering handlers.

* `:preload` fns fetch data from asynchronous sources (e.g. they return a promise)
* `:input` fns provide state to our system (e.g. get current state of atom)
* `:logic` fns transform state (e.g. set loading? flag)
* `:transition` fns process the state change (e.g. update atom, process effects)
* `:effect` fns undertake some kind of side-effect (e.g. GET request)  

# Getting started
Let's add an event loop to a reagent app.

## Basics
Our state will live in a reagent atom.
```cljs
(def app-db (r/atom {}))
```

We will start by registering a :db handler with :input and :transition fns.  These work to sample the app-db value before processing an event, then update it afterwards.
```cljs
(reg {:id :db
      :input (fn [] @app-db) 
      :transition (fn [_ db] (reset! app-db db)}))
```

Next, we're registering a :fx handler with a :transition fn which will process any side-effects required by our logic.
```cljs
(reg {:id :fx :transition do-effects})
```

The most common side-effect is dispatching events.  Let's register :dispatch handler with an :effect fn for that.

```cljs
(reg {:id :dispatch :effect dispatch})
```

Our logic will often want to reference the data passed with the event.  We'll add an :event handler with an :input fn for that.
```cljs
(reg {:id :event :input (fn [ctx] (:event ctx))})
```

Finally, we'll make the standard inputs available to all :logic handlers.  Individual handlers can add more.
```cljs
(cfg :std-ins [[:db] [:fx] [:event]])
```

Okay, the plumbing is in place.

## App logic

Now let's write some business logic.  They take state and transform it.

The most common tranformations our logic will make are
 1. updating the :db to change the application state e.g. `(assoc-in s [:db :loading?] true)` 
 2. updating the :fx to trigger some effect e.g. `(update s :fx conj {:dispatch [:some-event]})`

```cljs
(defn log-state [s] (println :log-state s) s)
(defn set-loading [s] (assoc-in s [:db :loading?] true))
(defn clear-loading [s] (update s :db dissoc :loading?))
(defn get-data [s] (update s :fx conj {::GET {:url "/endpoint/data" :cb #(dispatch [:app/get-resp %])}}))
(defn get-resp [s] (assoc-in s [:db :data] (get-in s [:event 1])))
(defn GET [{:keys [cb]}] (js/setTimeout #(cb {:results [1 2 3]}) 1000))
```

## Registering handlers
Now let's register our event handlers with :logic fns.  

```cljs
(reg {:id :app/bootstrap :logic (comp set-loading get-data log-state)})
(reg {:id :app/get-resp :logic (comp get-resp clear-loading log-state)})
```

We also need to register our :app/GET handler with :effect fn.
```cljs
(reg {:id :app/GET :effect GET})
```

## Debug
Perhaps it'll be useful to watch the app-db and observe what data is changing.

```cljs
(defn diff-report [[a b]] (println "app-db change:\n  only-before" a "\n  only-after" b))
(add-watch app-db ::app-db (fn [k _ o n] (diff-report (clojure.data/diff o n))))
```

## Dispatch
Did it work?  Let's dispatch an event and see.

```cljs
(dispatch [::bootstrap]))
```
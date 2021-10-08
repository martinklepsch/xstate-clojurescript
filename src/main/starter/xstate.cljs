(ns starter.xstate
  (:require ["xstate" :as xstate]
            [cljs-bean.core :as cljs-bean]
            [applied-science.js-interop :as j]))

(defn context [initial]
  #js {"__cljs_context" initial})

(def context-key
  "__cljs_context")

(defn get-context [context]
  (j/get context context-key))

(defn assign [updater]
  (xstate/assign
   (fn [context event]
     (j/assoc! context
               context-key
               (updater (get-context context) (cljs-bean/->clj event))))))

(defn guard [guard-fn]
  (fn [context event]
    (guard-fn (get-context context) (cljs-bean/->clj event))))

(defn ev
  ([ev-type]
   (ev ev-type nil))
  ([ev-type payload]
   (assert (string? ev-type))
   #js {:type ev-type :data (assoc payload :type ev-type)}))

(defn add-guards!
  [js-obj guards-map]
  (doseq [[k guard-fn] guards-map]
    (j/assoc-in! js-obj [:guards k] (guard guard-fn)))
  js-obj
  )

(defn add-actions!
  [js-obj actions-map]
  (doseq [[k action-fn] actions-map]
    (j/assoc-in! js-obj [:actions k] (assign action-fn)))
  js-obj)

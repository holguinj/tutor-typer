(ns tutor-typer.core
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(def scalar-preds
  [[number? :number]
   [nil?    :nil]
   [string? :string]
   [keyword? :keyword]])

(defn maybe-eval-scalar
  [maybe-scalar]
  (let [resolve-type (fn [[pred type]] (if (pred maybe-scalar) type))]
    (some resolve-type scalar-preds)))

(defn prettify-type
  [t]
  (cond
    (or (and (coll? t) (empty? t))
        (nil? t))
    ::empty-type

    (keyword? t)
    (str t)

    (and (set? t) (> (count t) 1) (contains? t :nil))
    (str "(t-maybe " (prettify-type (disj t :nil)) ")")

    (and (set? t) (> (count t) 1))
    (str "(t-union " (str/join " " t) ")")

    (set? t)
    (str (first (vec t)))

    (vector? t)
    (str "[" (str/join " " (mapv prettify-type t)) "]")

    :else
    (str t)))

(defn type-error
  [name expected actual]
  (throw (IllegalArgumentException.
          (format "Type error: %s expected %s, actual %s"
                  name
                  (prettify-type expected)
                  (prettify-type actual)))))

(defn type-match?
  [expected actual]
  (boolean
   (cond
     (and (set? expected)
          (set? actual))
     (some expected actual)

     (set? expected)
     (contains? expected actual)

     (set? actual)
     (every? (partial type-match? expected) actual)

     ;; this is an argument vector
     (vector? expected)
     (and (vector? actual) ;; args are also a vector
          (= (count expected) ;; that matches in arity
             (count actual))
          (every? true? (map type-match? expected actual)))

     :else
     (= expected actual))))

(defn fn-type
  [name domain range]
  (fn [args]
    (if (type-match? domain args)
      range
      (type-error name domain args))))

;; A function type takes a domain and range, then returns a function of
;; args -> range (or a type error)

(defn div
  [x y]
  (if-not (zero? y)
    (/ x y)))

(defn default-to
  [x default]
  (if (some? x)
    x
    default))

(defn t-union
  ([a b & more]
   (reduce t-union (t-union a b) more))
  ([a b]
   (cond
     (and (set? a)
          (set? b))
     (set/union a b)

     (set? a)
     (conj a b)

     (set? b)
     (conj b a)

     :else
     (conj #{} a b))))

(defn t-default-to
  [[x default]]
  (let [just-x (if (set? x) (disj x :nil) x)]
    (t-union just-x default)))

(defn t-maybe
  [t]
  (t-union :nil t))

(def fn-types
  {'+   (fn-type '+ [:number :number] :number)
   '-   (fn-type '- [:number :number] :number)
   'str (constantly :string)
   'div (fn-type 'div [:number :number] (t-maybe :number))
   'default-to t-default-to})

(declare type-check)

(defn maybe-eval-funcall
  [exp]
  (if (list? exp)
    (let [f (first exp)
          args (mapv type-check (rest exp))
          check (get fn-types f (constantly nil))]
      (check args))))

(defn type-check
  [exp]
  (or (maybe-eval-scalar exp)
      (maybe-eval-funcall exp)
      ::unknown))

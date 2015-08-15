(ns tutor-typer.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [tutor-typer.core :refer :all]))

(deftest maybe-eval-scalar-test
  (are [val type] (= type (maybe-eval-scalar val))
    "string" :string
    ""       :string
    123      :number
    0.123    :number
    1/4      :number
    nil      :nil
    :hello   :keyword
    '(+)     nil
    'foo     nil
    {}       nil
    #{}      nil))

(deftest prettify-type-test
  (are [input expected] (= expected (prettify-type input))
    #{:nil :string}           "(t-maybe :string)"
    #{:nil}                   ":nil"
    #{:string}                ":string"
    :string                   ":string"
    [:number :string]         "[:number :string]"
    [#{:nil :string} :number] "[(t-maybe :string) :number]"
    #{:number :string}        "(t-union :number :string)"
    #{:nil :number :string}   "(t-maybe (t-union :number :string))" ;; fragile!
    nil                       :tutor-typer.core/empty-type
    #{}                       :tutor-typer.core/empty-type))

(deftest type-match-test
  (testing "positive cases"
    (are [expected actual] (true? (type-match? expected actual))
      :string                           :string
      #{:nil :string}                   :string
      :string                           #{:string}
      #{:string}                        #{:string}
      [:number :string]                 [#{:number} :string]
      [#{:nil :number} #{:nil :string}] [:nil :string]
      [#{:nil :number} #{:nil :string}] [:nil :nil]
      [#{:nil :number} #{:nil :string}] [:number :string]))

  (testing "negative cases"
    (are [expected actual] (false? (type-match? expected actual))
      :string                           :nil
      #{:nil :string}                   :number
      :string                           #{:number}
      #{:string}                        #{:nil}
      [:number :string]                 [#{:nil :number} :string]
      [#{:nil :number} #{:nil :string}] [:nil]
      [#{:nil :number} #{:nil :string}] [:nil :number]
      [#{:nil :number} #{:nil :string}] [:nil :nil :string]
      [#{:nil :number} #{:nil :string}] :nil)))

(deftest t-union-test
  (are [types actual] (= actual (apply t-union types))
    [:nil :number]                 #{:nil :number}
    [#{:nil} #{:string}]           #{:nil :string}
    [:string #{:nil}]              #{:nil :string}
    [:string :string]              #{:string}
    [:string :number :nil]         #{:nil :string :number}
    [:number :string #{:nil} :nil] #{:nil :string :number}))

(deftest t-maybe-test
  (is (= #{:nil :string}
         (t-maybe :string)))
  (is (= #{:nil :number :string}
         (t-maybe #{:string :number}))))

(deftest maybe-eval-funcall-test
  (are [expression type] (= type (maybe-eval-funcall expression))
    '(+ 1 2)                   :number
    '(str (+ 1 2))             :string
    '(+ 1 (- 2 1))             :number
    '(+ 1 (+ 1 (+ 1 (+ 1 0)))) :number
    '(div 2 1)                 #{:nil :number}
    '(str (div 2 1))           :string
    '(default-to (div 2 1) 1)  #{:number} ;; fragile
    '()                        nil
    '(juxt :x :y)              nil
    "hello"                    nil))

(deftest type-check-test
  (testing "positive cases"
    (are [expression type] (= type (type-check expression))
      '(+ 1 (- 10 (default-to (div 12 0) 1)))
      :number

      "hi there"
      :string

      '(div 12 0)
      (t-maybe :number)

      '(str "hello " (+ 1 1) " you")
      :string

      '(str)
      :string

      123456
      :number))

  (testing "negative cases"
    (are [expression] (try (type-check expression)
                           nil
                           (catch IllegalArgumentException e
                             (re-find #"Type error" (.getMessage e))))
      '(+ 1 nil)
      '(+ 1 (div 10 2))
      '(+ 1 "hi")
      '(+ 1) ;; no single-arity plus yet
      '(+ 1 2 3) ;; no 3-arity plus yet
      '(+ 1 (str 2))))

  (testing "unknown cases"
    (are [expression] (= :tutor-typer.core/unknown (type-check expression))
      '()
      '(juxt :x :y)
      {:foo :bar}
      #{:sup})))

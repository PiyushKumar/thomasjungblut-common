package de.jungblut.classification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;

import de.jungblut.datastructure.ArrayUtils;
import de.jungblut.math.DoubleVector;
import de.jungblut.math.dense.DenseDoubleVector;
import de.jungblut.partition.BlockPartitioner;
import de.jungblut.partition.Boundaries.Range;

/**
 * Multi-class evaluator utility that takes care of test/train splitting and its
 * evaluation.
 * 
 * @author thomas.jungblut
 * 
 */
public final class Evaluator {

  public static class EvaluationResult {
    int numLabels, correct, trainSize, testSize, truePositive, falsePositive,
        trueNegative, falseNegative;

    public double getPrecision() {
      return ((double) truePositive) / (truePositive + falsePositive);
    }

    public double getRecall() {
      return ((double) truePositive) / (truePositive + falseNegative);
    }

    public double getAccuracy() {
      if (isBinary()) {
        return ((double) truePositive + falseNegative)
            / (truePositive + trueNegative + falsePositive + falseNegative);
      } else {
        return correct / (double) testSize;
      }
    }

    public double getF1Score() {
      return 2d * (getPrecision() * getRecall())
          / (getPrecision() + getRecall());
    }

    public int getCorrect() {
      if (!isBinary()) {
        return correct;
      } else {
        return truePositive + falseNegative;
      }
    }

    public int getNumLabels() {
      return numLabels;
    }

    public int getTrainSize() {
      return trainSize;
    }

    public int getTestSize() {
      return testSize;
    }

    public boolean isBinary() {
      return numLabels == 2;
    }

    public void add(EvaluationResult res) {
      numLabels += res.numLabels;
      correct += res.correct;
      trainSize += res.trainSize;
      testSize += res.testSize;
      truePositive += res.truePositive;
      falsePositive += res.falsePositive;
      trueNegative += res.trueNegative;
      falseNegative += res.falseNegative;
    }

    public void average(int pn) {
      double n = pn;
      numLabels /= n;
      correct /= n;
      trainSize /= n;
      testSize /= n;
      truePositive /= n;
      falsePositive /= n;
      trueNegative /= n;
      falseNegative /= n;
    }

    public void print() {
      System.out.println("Number of labels: " + getNumLabels());
      System.out.println("Trainingset size: " + getTrainSize());
      System.out.println("Testset size: " + getTestSize());
      System.out.println("Correctly classified: " + getCorrect());
      System.out.println("Accuracy: " + getAccuracy());
      if (isBinary()) {
        System.out.println("TP: " + truePositive);
        System.out.println("FP: " + falsePositive);
        System.out.println("TN: " + trueNegative);
        System.out.println("FN: " + falseNegative);
        System.out.println("Precision: " + getPrecision());
        System.out.println("Recall: " + getRecall());
        System.out.println("F1 Score: " + getF1Score());
      }
    }
  }

  /**
   * Trains and evaluates the given classifier with a test split.
   * 
   * @param classifier the classifier to train and evaluate.
   * @param features the features to split.
   * @param outcome the outcome to split.
   * @param numLabels the number of labels that are used. (e.G. 2 in binary
   *          classification).
   * @param splitPercentage a value between 0f and 1f that sets the size of the
   *          trainingset. With 1k items, a splitPercentage of 0.9f will result
   *          in 900 items to train and 100 to evaluate.
   * @param random true if you want to perform shuffling on the data beforehand.
   * @return a new {@link EvaluationResult}.
   */
  public static EvaluationResult evaluateClassifier(Classifier classifier,
      DoubleVector[] features, DenseDoubleVector[] outcome, int numLabels,
      float splitPercentage, boolean random) {
    return evaluateClassifier(classifier, features, outcome, numLabels,
        splitPercentage, random, null);
  }

  /**
   * Trains and evaluates the given classifier with a test split.
   * 
   * @param classifier the classifier to train and evaluate.
   * @param features the features to split.
   * @param outcome the outcome to split.
   * @param numLabels the number of labels that are used. (e.G. 2 in binary
   *          classification).
   * @param splitPercentage a value between 0f and 1f that sets the size of the
   *          trainingset. With 1k items, a splitPercentage of 0.9f will result
   *          in 900 items to train and 100 to evaluate.
   * @param random true if you want to perform shuffling on the data beforehand.
   * @param threshold in case of binary predictions, threshold is used to call
   *          in {@link Classifier#getPredictedClass(DoubleVector, double)}. Can
   *          be null, then no thresholding will be used.
   * @return a new {@link EvaluationResult}.
   */
  public static EvaluationResult evaluateClassifier(Classifier classifier,
      DoubleVector[] features, DenseDoubleVector[] outcome, int numLabels,
      float splitPercentage, boolean random, Double threshold) {

    Preconditions.checkArgument(numLabels > 1,
        "The number of labels should be greater than 1!");
    Preconditions.checkArgument(features.length == outcome.length,
        "Feature vector and outcome vector must match in length!");

    if (random) {
      ArrayUtils.multiShuffle(features, outcome);
    }

    final int splitIndex = (int) (features.length * splitPercentage);
    DoubleVector[] trainFeatures = ArrayUtils.subArray(features, splitIndex);
    DenseDoubleVector[] trainOutcome = ArrayUtils.subArray(outcome, splitIndex);
    DoubleVector[] testFeatures = ArrayUtils.subArray(features, splitIndex + 1,
        features.length - 1);
    DenseDoubleVector[] testOutcome = ArrayUtils.subArray(outcome,
        splitIndex + 1, outcome.length - 1);

    return evaluateSplit(classifier, numLabels, threshold, trainFeatures,
        trainOutcome, testFeatures, testOutcome);
  }

  /**
   * Evaluates a given train/test split with the given classifier.
   * 
   * @param classifier the classifier to train on the train split.
   * @param numLabels the number of labels that can be classified.
   * @param threshold the threshold for predicting a specific class by
   *          probability (if not provided = null).
   * @param trainFeatures the features to train with.
   * @param trainOutcome the outcomes to train with.
   * @param testFeatures the features to test with.
   * @param testOutcome the outcome to test with.
   * @return a fresh evalation result filled with the evaluated metrics.
   */
  public static EvaluationResult evaluateSplit(Classifier classifier,
      int numLabels, Double threshold, DoubleVector[] trainFeatures,
      DenseDoubleVector[] trainOutcome, DoubleVector[] testFeatures,
      DenseDoubleVector[] testOutcome) {
    EvaluationResult result = new EvaluationResult();
    result.numLabels = numLabels;
    result.testSize = testOutcome.length;
    result.trainSize = trainOutcome.length;

    classifier.train(trainFeatures, trainOutcome);

    // check the binary case to calculate special metrics
    if (numLabels == 2) {
      for (int i = 0; i < testFeatures.length; i++) {
        int outcomeClass = ((int) testOutcome[i].get(0));
        int prediction = 0;
        if (threshold == null) {
          prediction = classifier.getPredictedClass(testFeatures[i]);
        } else {
          prediction = classifier.getPredictedClass(testFeatures[i], threshold);
        }
        if (outcomeClass == 1) {
          if (outcomeClass == prediction) {
            result.truePositive++;
          } else {
            result.trueNegative++;
          }
        } else {
          if (outcomeClass == prediction) {
            result.falseNegative++;
          } else {
            result.falsePositive++;
          }
        }
      }
    } else {
      for (int i = 0; i < testFeatures.length; i++) {
        int outcomeClass = testOutcome[i].maxIndex();
        int prediction = classifier.getPredictedClass(testFeatures[i]);
        if (outcomeClass == prediction) {
          result.correct++;
        }
      }
      // TODO confusion matrix
    }
    return result;
  }

  /**
   * Does a k-fold crossvalidation on the given classifiers with features and
   * outcomes.
   * 
   * @param classifierFactory the classifiers to train and test.
   * @param features the features to train/test with.
   * @param outcome the outcomes to train/test with.
   * @param numLabels the total number of labels that are possible. e.G. 2 in
   *          the binary case.
   * @param folds the number of folds to fold, usually 10.
   * @param threshold the threshold for predicting a specific class by
   *          probability (if not provided = null).
   * @param verbose true if partial fold results should be printed.
   * @return a averaged evaluation result over all k folds.
   */
  public static EvaluationResult crossValidateClassifier(
      ClassifierFactory classifierFactory, DoubleVector[] features,
      DenseDoubleVector[] outcome, int numLabels, int folds, Double threshold,
      boolean verbose) {
    // train on k-1 folds, test on 1 fold, results are averaged
    final int numFolds = folds + 1;
    // multi shuffle the arrays first, note that this is not stratified.
    ArrayUtils.multiShuffle(features, outcome);

    EvaluationResult averagedModel = new EvaluationResult();
    int m = features.length;
    // compute the split ranges by blocks, so we have range from 0 to the next
    // partition index end that will be our testset, and so on.
    List<Range> partition = new ArrayList<>(new BlockPartitioner().partition(
        numFolds, m).getBoundaries());
    int[] splitRanges = new int[numFolds];
    for (int i = 1; i < numFolds; i++) {
      splitRanges[i] = partition.get(i).getEnd();
    }

    // because we are dealing with indices, we have to subtract 1 from the end
    splitRanges[numFolds - 1] = splitRanges[numFolds - 1] - 1;

    if (verbose) {
      System.out.println("Computed split ranges: "
          + Arrays.toString(splitRanges) + "\n");
    }

    // build the models fold for fold
    for (int fold = 0; fold < folds; fold++) {
      DoubleVector[] featureTest = ArrayUtils.subArray(features,
          splitRanges[fold], splitRanges[fold + 1]);
      DenseDoubleVector[] outcomeTest = ArrayUtils.subArray(outcome,
          splitRanges[fold], splitRanges[fold + 1]);
      DoubleVector[] featureTrain = new DoubleVector[m - featureTest.length];
      DenseDoubleVector[] outcomeTrain = new DenseDoubleVector[m
          - featureTest.length];
      int index = 0;
      for (int i = 0; i < m; i++) {
        if (i < splitRanges[fold] || i > splitRanges[fold + 1]) {
          featureTrain[index] = features[i];
          outcomeTrain[index] = outcome[i];
          index++;
        }
      }

      EvaluationResult foldSplit = evaluateSplit(
          classifierFactory.newInstance(), numLabels, threshold, featureTrain,
          outcomeTrain, featureTest, outcomeTest);

      if (verbose) {
        System.out.println("Fold: " + (fold + 1));
        foldSplit.print();
        System.out.println();
      }
      averagedModel.add(foldSplit);
    }

    // average the sums in the model
    averagedModel.average(folds);
    return averagedModel;
  }

  /**
   * Does a 10 fold crossvalidation.
   * 
   * @param classifierFactory the classifiers to train and test.
   * @param features the features to train/test with.
   * @param outcome the outcomes to train/test with.
   * @param numLabels the total number of labels that are possible. e.G. 2 in
   *          the binary case.
   * @param threshold the threshold for predicting a specific class by
   *          probability (if not provided = null).
   * @param verbose true if partial fold results should be printed.
   * @return a averaged evaluation result over all 10 folds.
   */
  public static EvaluationResult tenFoldCrossValidation(
      ClassifierFactory classifierFactory, DoubleVector[] features,
      DenseDoubleVector[] outcome, int numLabels, Double threshold,
      boolean verbose) {
    return crossValidateClassifier(classifierFactory, features, outcome,
        numLabels, 10, threshold, verbose);
  }

}

package recsys
import java.util.Random
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd._
import org.apache.spark.mllib.recommendation.{ALS, Rating, MatrixFactorizationModel}

object AMP {
  
    /** Elicitate ratings from command-line. */
	  def elicitateRatings(movies: Seq[(Int, String)]) = {
	    List(Rating(0,1193,5),Rating(0,661,3),Rating(0,914,3))
	    // ...
	  }
  /** Compute RMSE (Root Mean Squared Error). */
	  
  def computeRmse(model: MatrixFactorizationModel, data: RDD[Rating], n: Long) = {
    val userProducts = data.map{case Rating(user,product,rate) => (user,product)}
    val predictions = model.predict(userProducts).map{case Rating(user,product,rate) => ((user,product),rate)}
    val ratesAndPreds= predictions.join(data.map{
      case Rating(user,product,rate) => ((user,product),rate)}
    )
    
    
    val MSE = ratesAndPreds.map { case ((user, product), (r1, r2)) => 
    val err = (r1 - r2)
     err * err
     }.mean()
    
    MSE
    //println("Mean Squared Error = " + MSE)
     
    // ...
  }
	  
   
  
    def main(args:Array[String]){
    val sc = new SparkContext("local[4]", "movlens",System.getenv("SPARK_HOME"))
    val data = sc.textFile("/home/xunw/data/MovieLens/ml-1m/ratings.dat")
    
    val ratings =data.map { line =>
      val fields = line.split("::")
      // format: (timestamp % 10, Rating(userId, movieId, rating))
      (fields(3).toLong % 10, Rating(fields(0).toInt, fields(1).toInt, fields(2).toDouble))
    }
    
     val movies = sc.textFile("/home/xunw/data/MovieLens/ml-1m//movies.dat").map { line =>
      val fields = line.split("::")
      // format: (movieId, movieName)
      (fields(0).toInt, fields(1))
    }.collect.toMap
    
    val numRatings = ratings.count
    val numUsers = ratings.map(_._2.user).distinct.count
    val numMovies = ratings.map(_._2.product).distinct.count

    println("Got " + numRatings + " ratings from "
      + numUsers + " users on " + numMovies + " movies.")
     
    
    val mostRatedMovieIds = ratings.map(_._2.product) // extract movie ids    
                                   .countByValue      // count ratings per movie    //Map[int,long]
                                   .toSeq             // convert map to Seq
                                   .sortBy(- _._2)    // sort by rating count
                                   .take(50)          // take 50 most rated
                                   .map(_._1)         // get their ids
   
                                   
                                   
    val random = new Random(0)
    val selectedMovies = mostRatedMovieIds.filter(x => random.nextDouble() < 0.2)
                                          .map(x => (x, movies(x)))
                                          .toSeq
                                          
    val myRatings = elicitateRatings(selectedMovies)
    val myRatingsRDD = sc.parallelize(myRatings)                                     
    
    val numPartitions = 4
    
    val training = ratings.filter(x => x._1 < 6)                  //pairRdd.values
                          .values
                          .union(myRatingsRDD)
                          .repartition(numPartitions)
                          .persist
    val validation = ratings.filter(x => x._1 >= 6 && x._1 < 8)
                            .values
                            .repartition(numPartitions)
                            .persist
                            
    val test = ratings.filter(x => x._1 >= 8).values.persist

    val numTraining = training.count
    val numValidation = validation.count
    val numTest = test.count

    println("Training: " + numTraining + ", validation: " + numValidation + ", test: " + numTest)
   
    def train(ratings: RDD[Rating], rank: Int, iterations: Int, lambda: Double)
    : MatrixFactorizationModel = {
	    val ranks = List(8, 12)
	    val lambdas = List(0.1, 0.5)
	    val numIters = List(10, 20)
	    var bestModel: Option[MatrixFactorizationModel] = None
	    var bestValidationRmse = Double.MaxValue
	    var bestRank = 0
	    var bestLambda = -1.0
		    var bestNumIter = -1
	    for (rank <- ranks; lambda <- lambdas; numIter <- numIters) {
	      val model = ALS.train(training, rank, numIter, lambda)  //train
	      val validationRmse = computeRmse(model, validation, numValidation)
	      
	      println("RMSE (validation) = " + validationRmse + " for the model trained with rank = "
	        + rank + ", lambda = " + lambda + ", and numIter = " + numIter + ".")
	      if (validationRmse < bestValidationRmse) {
	        bestModel = Some(model)
	        bestValidationRmse = validationRmse
	        bestRank = rank
	        bestLambda = lambda
	        bestNumIter = numIter
	      }
	    }
	   println("The best model was trained with rank = " + bestRank + " and lambda = " + bestLambda
      + ", and numIter = " + bestNumIter )
	    bestModel.get
	   }
	    //val testRmse = computeRmse(bestModel.get, test, numTest)
	val bestALSModel = train(training,10,20,0.1)
	val testRmse = computeRmse(bestALSModel, test, numTest)
	println("RMSE on the test set is " + testRmse)
	
	val myRatedMovieIds = myRatings.map(_.product).toSet
	val candidates = sc.parallelize(movies.keys.filter(!myRatedMovieIds.contains(_)).toSeq)
	val recommendations =  bestALSModel.predict(candidates.map((0, _)))
                                   .collect
                                   .sortBy(-_.rating)
                                   .take(50)
    var i = 1
    println("Movies recommended for you:")
    recommendations.foreach { 
	  r =>  println("%2d".format(i) + ": " + movies(r.product))
      i += 1    }                          
    sc.stop()
    
    }
    

}
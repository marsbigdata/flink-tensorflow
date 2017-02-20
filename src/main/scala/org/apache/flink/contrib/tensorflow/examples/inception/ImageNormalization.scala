package org.apache.flink.contrib.tensorflow.examples.inception

import java.util.{List => JavaList}

import com.twitter.bijection.Conversion._
import org.apache.flink.contrib.tensorflow.examples.common.GraphBuilder
import org.apache.flink.contrib.tensorflow.examples.inception.ImageNormalization._
import org.apache.flink.contrib.tensorflow.examples.inception.ImageNormalizationMethod._
import org.apache.flink.contrib.tensorflow.models.generic.{GenericModel, GraphDefGraphLoader, GraphLoader}
import org.apache.flink.contrib.tensorflow.models.{ModelFunction, ModelMethod}
import org.apache.flink.contrib.tensorflow.types.TensorInjections._
import org.slf4j.{Logger, LoggerFactory}
import org.tensorflow._
import org.tensorflow.framework.{SignatureDef, TensorInfo}

/**
  * Decodes and normalizes a JPEG image (as a byte[]) as a 4D tensor.
  *
  * <p>The output is compatible with inception5h.
  */
@SerialVersionUID(1L)
class ImageNormalization extends GenericModel[ImageNormalization] {

  protected val (graphDef, signatureDef) = {
    try {
      val b: GraphBuilder = new GraphBuilder
      try {
        // Some constants specific to the pre-trained model at:
        // https://storage.googleapis.com/download.tensorflow.org/models/inception5h.zip
        //
        // - The inception model was trained with images scaled to 224x224 pixels.
        // - The colors, represented as R, G, B in 1-byte each were converted to
        //   float using (value - Mean)/Scale.
        val H: Int = 224
        val W: Int = 224
        val mean: Float = 117f
        val scale: Float = 1f

        // Since the graph is being constructed once per execution here, we can use a constant for the
        // input image. If the graph were to be re-used for multiple input images, a placeholder would
        // have been more appropriate.
        val input: Output = b.constant("input", INPUT_IMAGE_TEMPLATE)
        val output: Output = b.div(
          b.sub(
            b.resizeBilinear(
              b.expandDims(
                b.cast(b.decodeJpeg(input, 3), DataType.FLOAT),
                b.constant("make_batch", 0)),
              b.constant("size", Array[Int](H, W))),
            b.constant("mean", mean)),
          b.constant("scale", scale))

        val signatureDef = SignatureDef.newBuilder()
          .setMethodName(NORMALIZE_METHOD_NAME)
          .putInputs(NORMALIZE_INPUTS, TensorInfo.newBuilder().setName(input.op.name).build())
          .putOutputs(NORMALIZE_OUTPUTS, TensorInfo.newBuilder().setName(output.op.name).build())
          .build()

        (b.buildGraphDef(), signatureDef)
      } finally {
        b.close()
      }
    }
  }

  override protected def graphLoader: GraphLoader = new GraphDefGraphLoader(graphDef)

  /**
    * Normalizes an image to a 4-D tensor value.
    */
  def normalize = ModelFunction[ImageNormalizationMethod](session, signatureDef)
}

object ImageNormalization {

  private[inception] val LOG: Logger = LoggerFactory.getLogger(classOf[ImageNormalization])

  private[inception] val INPUT_IMAGE_TEMPLATE: Array[Byte] = new Array[Byte](86412)

}

sealed trait ImageNormalizationMethod extends ModelMethod {
  val name = NORMALIZE_METHOD_NAME
}

object ImageNormalizationMethod {
  val NORMALIZE_METHOD_NAME = "inception/normalize"
  val NORMALIZE_INPUTS = "inputs"
  val NORMALIZE_OUTPUTS = "outputs"

  /**
    * Normalizes a vector of image files to a vector of images.
    * @param input the images as a [[ImageFileTensor]]
    * @return the image data as a [[ImageTensor]]
    */
  implicit def fromByteString(input: ImageFileTensor) =
    new ImageNormalizationMethod {
      type Result = ImageTensor
      def inputs(): Map[String, Tensor] = Map(NORMALIZE_INPUTS -> input.toTensor)
      def outputs(o: Map[String, Tensor]): Result = o(NORMALIZE_OUTPUTS).as[Option[ImageTensor]].get
    }
}

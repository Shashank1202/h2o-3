package ai.h2o.automl;

import hex.FrameSplitter;
import hex.Model;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.*;
import water.H2O;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.ast.prims.mungers.AstGroup;
import water.util.FrameUtils;
import water.util.IcedHashMap;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;
import static water.util.FrameUtils.generateNumKeys;

public class TargetEncodingTest extends TestUtil{


  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  @Before
  public void beforeEach() {
        System.out.println("Before each setup");
    }

  /*@Ignore
  @Test public void TitanicDemoWithTargetEncodingTest() {
    Frame fr=null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");


      fr = FrameUtils.categoricalEncoder(fr, new String[]{},
              Model.Parameters.CategoricalEncodingScheme.LabelEncoder, null, -1);

      // Splitting phase

      double[] ratios  = ard(0.7f, 0.1f, 0.1f);
      Frame[] splits  = null;
      long numberOfRows = fr.numRows();
      FrameSplitter fs = new FrameSplitter(fr, ratios, generateNumKeys(fr._key, ratios.length+1), null);
      H2O.submitTask(fs).join();
      splits = fs.getResult();
      Frame train = splits[0];
      Frame valid = splits[1];
      Frame te_holdout = splits[2];
      Frame test = splits[3];
      long l = train.numRows();
      double v = Math.floor(numberOfRows * 0.7);
      assert l == v;
      assert splits.length == 4;

      String[] colNames = train.names();

      //myX <- setdiff(colnames(train), c("survived", "name", "ticket", "boat", "body"))


      // Building default GBM
      GBMModel gbm = null;
      Frame fr2 = null;

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = train._key;
      parms._response_column = "survived";
      parms._ntrees = 2;
      parms._max_depth = 3;
      parms._distribution = DistributionFamily.AUTO;
      parms._keep_cross_validation_predictions=true;
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      Assert.assertTrue(job.isStopped());

      fr2 = gbm.score(fr);

    } finally {
      if(fr != null) fr.remove();
    }
  }
*/
    @Test(expected = IllegalStateException.class)
    public void targetEncoderPrepareEncodingFrameValidationDataIsNotNullTest() {

        TargetEncoder tec = new TargetEncoder();
        String[] teColumns = {"0"};

        tec.prepareEncodingMap(null, teColumns, "2", null);
    }


    @Test(expected = IllegalStateException.class)
    public void targetEncoderPrepareEncodingFrameValidationTEColumnsIsNotEmptyTest() {

        TargetEncoder tec = new TargetEncoder();
        String[] teColumns = {};

        tec.prepareEncodingMap(null, teColumns, "2", null);

    }

    @Test
    public void targetEncoderPrepareEncodingFrameValidationTest() {

        //TODO test other validation checks

    }

    @Test
    public void targetEncoderFilterOutNAsTest() {

        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_STR)
                .withDataForCol(0, ard(1, 1))
                .withDataForCol(1, ard(1, 1))
                .withDataForCol(2, ar(null, "6"))
                .build();
        TargetEncoder tec = new TargetEncoder();
        Frame result = tec.filterOutNAsFromTargetColumn(fr, 2);
        assertEquals(1L, result.numRows());

    }

    @Test
    public void allTEColumnsAreCategoricalTest() {

        TestFrameBuilder baseBuilder = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC")
                .withDataForCol(0, ar("1", "0"))
                .withDataForCol(2, ar("1", "6"));

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0, 1};

        fr = baseBuilder
                .withDataForCol(1, ar(0, 1))
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                .build();
        try {
            tec.prepareEncodingMap(fr, teColumns, 2, null);
            fail();
        } catch (IllegalStateException ex) {
            assertEquals("Argument 'columnsToEncode' should contain only names of categorical columns", ex.getMessage());
        }

        fr = baseBuilder
                .withDataForCol(1, ar("a", "b"))
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
                .build();

        try {
            tec.prepareEncodingMap(fr, teColumns, 2, null);
        } catch (IllegalStateException ex) {
            fail(String.format("All columns were categorical but something else went wrong: %s", ex.getMessage()));
        }
    }

    @Test
    public void prepareEncodingMapForKFoldCaseTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b"))
                .withDataForCol(1, ard(1, 1, 4, 7))
                .withDataForCol(2, ar("2", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame colAEncoding = targetEncodingMap.get("ColA");
        TwoDimTable twoDimTable = colAEncoding.toTwoDimTable();
        System.out.println(twoDimTable.toString());

        assertVecEquals(vec(0, 2, 1), colAEncoding.vec(2), 1e-5);
        assertVecEquals(vec(1, 2, 1), colAEncoding.vec(3), 1e-5);

    }

    @Test
    public void prepareEncodingMapWithoutFoldColumnCaseTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b", "b", "b"))
                .withDataForCol(1, ard(1, 1, 4, 7))
                .withDataForCol(2, ar("2", "6", "6", "6"))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2);

        Frame colAEncoding = targetEncodingMap.get("ColA");
        TwoDimTable twoDimTable = colAEncoding.toTwoDimTable();
        System.out.println(twoDimTable.toString());

        assertVecEquals(vec(0, 3), colAEncoding.vec(1), 1e-5);
        assertVecEquals(vec(1, 3), colAEncoding.vec(2), 1e-5);

    }

    @Test
    public void prepareEncodingMapForKFoldCaseWithSomeOfTheTEValuesRepresentedOnlyInOneFold_Test() {
        //TODO like in te_encoding_possible_bug_demo.R test
    }

    // ------------------------ KFold holdout type -------------------------------------------------------------------//
    @Test
    public void targetEncoderKFoldHoldoutApplyingTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 3, false, 0, 1234.0);

        TwoDimTable resultTable = resultWithEncoding.toTwoDimTable();
        System.out.println("Result table" + resultTable.toString());
        Vec vec = resultWithEncoding.vec(4);
        assertVecEquals(vec(1,0,1,1,1), vec, 1e-5);
    }

    @Test
    public void targetEncoderKFoldHoldout_WithNonZeroColumnToEncode_ApplyingTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColA2", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ar("a", "b", "b", "b", "a"))
                .withDataForCol(2, ard(1, 1, 4, 7, 4))
                .withDataForCol(3, ar("2", "6", "6", "6", "6"))
                .withDataForCol(4, ar(1, 2, 2, 3, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {1};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 3, 4);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 3, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 4, false, 0, 1234.0);

        TwoDimTable resultTable = resultWithEncoding.toTwoDimTable();
        System.out.println("Result table" + resultTable.toString());
        Vec vec = resultWithEncoding.vec(5);
        assertVecEquals(vec(1,0,1,1,1), vec, 1e-5);
    }

    @Test
    public void targetEncoderKFoldHoldoutApplyingWithoutFoldColumnTest() {
      //TODO fold_column = null case
    }

    @Test
    public void targetEncoderKFoldHoldoutApplyingWithNoiseTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        //If we do not pass noise_level as parameter then it will be calculated according to the type of target column. For categorical target column it defaults to 1e-2
        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 3, false);

        TwoDimTable resultTable = resultWithEncoding.toTwoDimTable();
        System.out.println("Result table" + resultTable.toString());
        assertVecEquals(vec(1,0,1,1,1), resultWithEncoding.vec(4), 1e-2); // TODO if it's ok that encoding contains negative values?
    }

    @Test
    public void targetEncoderKFoldHoldoutApplyingWithCustomNoiseTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 3, false, 0.02, 1234.0);

        TwoDimTable resultTable = resultWithEncoding.toTwoDimTable();
        System.out.println("Result table" + resultTable.toString());
        assertVecEquals(vec(1,0,1,1,1), resultWithEncoding.vec(4), 2e-2); // TODO we do not check here actually that we have noise more then default 0.01. We need to check that sometimes we get 0.01 < delta < 0.02
    }

    @Test
    public void targetEncoderKFoldHoldoutApplyingWithCustomNoiseForNumericColumnTest() {
        //TODO
    }

    @Test
    public void calculateSingleNumberResultTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA")
                .withVecTypes(Vec.T_NUM)
                .withDataForCol(0, ard(1, 2, 3))
                .build();
        String tree = "(sum (cols testFrame [0.0] ))";
        Val val = Rapids.exec(tree);
        assertEquals(val.getNum(), 6.0, 1e-5);
    }

    @Test
    public void calculateGlobalMeanTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ard(1, 2, 3))
                .withDataForCol(1, ard(3, 4, 5))
                .build();
        TargetEncoder tec = new TargetEncoder();
        double result = tec.calculateGlobalMean(fr, 0, 1);

        assertEquals(result, 0.5, 1e-5);
    }

    // ----------------------------- blended average -----------------------------------------------------------------//
    @Test
    public void calculateAndAppendBlendedTEEncodingTest() {

        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ard(1, 2, 3))
                .withDataForCol(1, ard(3, 4, 5))
                .build();
        TargetEncoder tec = new TargetEncoder();
        Frame result = tec.calculateAndAppendBlendedTEEncoding(fr, 0, 1, "targetEncoded" );

        TwoDimTable twoDimTable = result.toTwoDimTable();
        System.out.println(twoDimTable.toString());

        // k <- 20
        // f <- 10
        // global_mean <- sum(x_map$numerator)/sum(x_map$denominator)
        // lambda <- 1/(1 + exp((-1)* (te_frame$denominator - k)/f))
        // te_frame$target_encode <- ((1 - lambda) * global_mean) + (lambda * te_frame$numerator/te_frame$denominator)

        double globalMean = (1.0 + 2.0 + 3.0) / (3 + 4 + 5);
        double lambda1 = 1.0 /( 1 + Math.exp( (20.0 - 3) / 10));
        double te1 = (1 - lambda1) * globalMean + ( lambda1 * 1 / 3);

        double lambda2 = 1.0 /( 1 + Math.exp( (20.0 - 4) / 10));
        double te2 = (1.0 - lambda2) * globalMean + ( lambda2 * 2 / 4);

        double lambda3 = 1.0 /( 1 + Math.exp( (20.0 - 5) / 10));
        double te3 = (1.0 - lambda3) * globalMean + ( lambda3 * 3 / 5);

        assertEquals(te1, result.vec(2).at(0), 1e-5);
        assertEquals(te2, result.vec(2).at(1), 1e-5);
        assertEquals(te3, result.vec(2).at(2), 1e-5);

    }

    @Test
    public void targetEncoderKFoldHoldoutApplyingWithBlendedAvgTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 3, true, 0.0, 1234.0);

        printOutFrameAsTable(resultWithEncoding);

        Vec encodedVec = resultWithEncoding.vec(4);

        // TODO I'm not sure if the values are correct but we at least can fix them and avoid regression while changing code further.
        assertEquals(0.855, encodedVec.at(0), 1e-3);
        assertEquals(0.724, encodedVec.at(1), 1e-3);
        assertEquals( 0.855, encodedVec.at(2), 1e-3);
        assertEquals( 0.856, encodedVec.at(4), 1e-3);
    }

    // ------------------------ LeaveOneOut holdout type -------------------------------------------------------------//

    @Test
    public void targetEncoderLOOHoldoutDivisionByZeroTest() {
      //TODO Check division by zero when we subtract current row's value and then use results to calculate numerator/denominator
    }

    @Test
    public void targetEncoderLOOHoldoutSubtractCurrentRowTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "numerator", "denominator", "target")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b", "b", "b", "a", "b"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4, 2))
                .withDataForCol(2, ard(1, 1, 4, 7, 4, 6))
                .withDataForCol(3, ar("2", "6", "6", "6", "6", null))
                .build();

        TargetEncoder tec = new TargetEncoder();
        printOutFrameAsTable(fr);

        Frame res = tec.subtractTargetValueForLOO(fr, 1, 2, 3);
        printOutFrameAsTable(res);

        // We check here that for  `target column = NA` we do not subtract anything and for other cases we subtract current row's target value
        Vec vecNotSubtracted = vec(1, 0, 3, 6, 3, 2);
        assertVecEquals(vecNotSubtracted, res.vec(1), 1e-5);
        Vec vecSubtracted = vec(0, 0, 3, 6, 3, 6);
        assertVecEquals(vecSubtracted, res.vec(2), 1e-5);
    }

    @Test
    public void vecESPCTest() {
        Vec vecOfALengthTwo = vec(1, 0);
        long [] espcForLengthTwo = {0, 2};
        assertArrayEquals(espcForLengthTwo, Vec.ESPC.espc(vecOfALengthTwo));
        Vec vecOfALengthThree = vec(1, 0, 3);
        long [] espcForVecOfALengthThree = {0, 3};
        assertArrayEquals(espcForVecOfALengthThree, Vec.ESPC.espc(vecOfALengthThree));
    }

    @Test
    public void targetEncoderLOOHoldoutApplyingTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.LeaveOneOut,false, 0, 1234.0);

        TwoDimTable resultTable = resultWithEncoding.toTwoDimTable();
        System.out.println("Result table" + resultTable.toString());
        assertVecEquals(vec(1,0,1,1,1), resultWithEncoding.vec(3), 1e-5);
    }

    @Test
    public void targetEncoderLOOHoldoutApplyingWithFoldColumnTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.LeaveOneOut, 3,false, 0, 1234.0);

        assertVecEquals(vec(1,0,1,1,1), resultWithEncoding.vec(4), 1e-5);
    }

    // ------------------------ None holdout type --------------------------------------------------------------------//

    @Test
    public void targetEncoderNoneHoldoutApplyingTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.None, 3,false, 0, 1234.0);

        TwoDimTable resultTable = resultWithEncoding.toTwoDimTable();
        System.out.println("Result table" + resultTable.toString());
        Vec vec = resultWithEncoding.vec(4);
        assertEquals(0.5, vec.at(0), 1e-5);
        assertEquals(0.5, vec.at(1), 1e-5);
        assertEquals(1, vec.at(2), 1e-5);
        assertEquals(1, vec.at(3), 1e-5);
        assertEquals(1, vec.at(4), 1e-5);
    }

    // ------------------------ Multiple columns for target encoding -------------------------------------------------//

    @Test
    public void KFoldHoldoutMultipleTEColumnsWithFoldColumnTest() {
        TestFrameBuilder frameBuilder = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ar("d", "e", "d", "e", "e"))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2));

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0, 1};

        fr = frameBuilder.withName("testFrame").build();

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 3,false, 0, 1234.0);
        Vec encodingForColumnA_Multiple = resultWithEncoding.sort(new int[]{2}).vec(4);
        Vec encodingForColumnB_Multiple = resultWithEncoding.sort(new int[]{0}).vec(5);

        //Let's check it with Single TE version of the algorithm. So we rely here on a correctness of the single-column encoding.
        //  For the first encoded column
        Frame frA = frameBuilder.withName("testFrameA").build();

        int[] indexForColumnA = Arrays.copyOfRange(teColumns, 0, 1);
        Map<String, Frame> targetEncodingMapForColumn1 = tec.prepareEncodingMap(frA, indexForColumnA, 2, 3);
        Frame resultWithEncodingForColumn1 = tec.applyTargetEncoding(frA, indexForColumnA, 2, targetEncodingMapForColumn1, TargetEncoder.HoldoutType.KFold, 3,false, 0, 1234.0);
        Vec encodingForColumnA_Single = resultWithEncodingForColumn1.vec(4);

        assertVecEquals(encodingForColumnA_Single, encodingForColumnA_Multiple, 1e-5);

        // For the second encoded column
        Frame frB = frameBuilder.withName("testFrameB").build();

        int[] indexForColumnB = Arrays.copyOfRange(teColumns, 1, 2);
        Map<String, Frame> targetEncodingMapForColumn2 = tec.prepareEncodingMap(frB, indexForColumnB, 2, 3);
        Frame resultWithEncodingForColumnB = tec.applyTargetEncoding(frB, indexForColumnB, 2, targetEncodingMapForColumn2, TargetEncoder.HoldoutType.KFold, 3,false, 0, 1234.0);
        Vec encodingForColumnB_Single = resultWithEncodingForColumnB.vec(4);

        assertVecEquals(encodingForColumnB_Single, encodingForColumnB_Multiple, 1e-5);
    }

    @Test
    public void LOOHoldoutMultipleTEColumnsWithFoldColumnTest() {
        TestFrameBuilder frameBuilder = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ar("d", "e", "d", "e", "e"))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2));

        fr = frameBuilder.withName("testFrame").build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0, 1};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.LeaveOneOut, 3,false, 0, 1234.0);
        Vec encodingForColumnA_Multiple = resultWithEncoding.sort(new int[]{1}).vec(4);
        Vec encodingForColumnB_Multiple = resultWithEncoding.sort(new int[]{0}).vec(5);

        // Let's check it with Single TE version of the algorithm. So we rely here on a correctness of the single-column encoding.
        //  For the first encoded column
        Frame frA = frameBuilder.withName("testFrameA").build();

        int[] indexForColumnA = Arrays.copyOfRange(teColumns, 0, 1);
        Map<String, Frame> targetEncodingMapForColumn1 = tec.prepareEncodingMap(frA, indexForColumnA, 2, 3);
        Frame resultWithEncodingForColumn1 = tec.applyTargetEncoding(frA, indexForColumnA, 2, targetEncodingMapForColumn1, TargetEncoder.HoldoutType.LeaveOneOut, 3,false, 0, 1234.0);
        Vec encodingForColumnA_Single = resultWithEncodingForColumn1.vec(4);

        assertVecEquals(encodingForColumnA_Single, encodingForColumnA_Multiple, 1e-5);

        // For the second encoded column
        Frame frB = frameBuilder.withName("testFrameB").build();

        int[] indexForColumnB = Arrays.copyOfRange(teColumns, 1, 2);
        Map<String, Frame> targetEncodingMapForColumn2 = tec.prepareEncodingMap(frB, indexForColumnB, 2, 3);
        Frame resultWithEncodingForColumnB = tec.applyTargetEncoding(frB, indexForColumnB, 2, targetEncodingMapForColumn2, TargetEncoder.HoldoutType.LeaveOneOut, 3,false, 0, 1234.0);
        Vec encodingForColumnB_Single = resultWithEncodingForColumnB.vec(4);

        assertVecEquals(encodingForColumnB_Single, encodingForColumnB_Multiple, 1e-5);
    }

    @Test
    public void NoneHoldoutMultipleTEColumnsWithFoldColumnTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ar("d", "e", "d", "e", "e"))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0, 1};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.None, 3,false, 0, 1234.0);

        // TODO We need vec(..) for doubles to make it easier.
        // For the first encoded column
        assertEquals(0.5, resultWithEncoding.vec(4).at(0), 1e-5);
        assertEquals(1, resultWithEncoding.vec(4).at(1), 1e-5);
        assertEquals(0.5, resultWithEncoding.vec(4).at(2), 1e-5);
        assertEquals(1, resultWithEncoding.vec(4).at(3), 1e-5);
        assertEquals(1, resultWithEncoding.vec(4).at(4), 1e-5);

        // For the second encoded column
        assertEquals(0.5, resultWithEncoding.vec(5).at(0), 1e-5);
        assertEquals(0.5, resultWithEncoding.vec(5).at(1), 1e-5);
        assertEquals(1, resultWithEncoding.vec(5).at(2), 1e-5);
        assertEquals(1, resultWithEncoding.vec(5).at(3), 1e-5);
        assertEquals(1, resultWithEncoding.vec(5).at(4), 1e-5);
    }

    @Test
    public void AddNoiseLevelTest() {

        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA")
                .withVecTypes(Vec.T_NUM)
                .withDataForCol(0, ard(1, 2, 3))
                .build();

        double noiseLevel = 1e-2;
        TargetEncoder tec = new TargetEncoder();
        fr = tec.addNoise(fr, "ColA", noiseLevel, 1234.0);
        assertVecEquals(vec(1, 2, 3), fr.vec(0), 1e-2);
    }

    @Test
    public void getColumnIndexByNameTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b"))
                .withDataForCol(1, ard(1, 1))
                .withDataForCol(2, ar("2", "6"))
                .withDataForCol(3, ar(1, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        assertEquals(2, tec.getColumnIndexByName(fr, "ColC"));
        assertEquals(3, tec.getColumnIndexByName(fr, "fold_column"));
    }

    @Test
    public void getColumnNamesByIndexesTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b"))
                .withDataForCol(1, ard(1, 1))
                .withDataForCol(2, ar("2", "6"))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] columns = ari(0,2);
        String [] columnNames = tec.getColumnNamesBy(fr, columns);
        assertEquals("ColA", columnNames[0]);
        assertEquals("ColC", columnNames[1]);
    }

    @Test
    public void renameColumnTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b"))
                .withDataForCol(1, ard(1, 1))
                .withDataForCol(2, ar("2", "6"))
                .withDataForCol(3, ar(1, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();

        // Case1: Renaming by index
        String indexOfColumnToRename = "0";
        String newName = "NewColA";
        Frame renamedFrame = tec.renameColumn(fr, Integer.parseInt(indexOfColumnToRename), newName);

        TwoDimTable twoDimTable = renamedFrame.toTwoDimTable();
        assertEquals( twoDimTable.getColHeaders()[Integer.parseInt(indexOfColumnToRename)], newName);

        // Case2: Renaming by name
        String newName2 = "NewColA-2";
        renamedFrame = tec.renameColumn(fr, "NewColA", newName2);
        TwoDimTable table = renamedFrame.toTwoDimTable();
        assertEquals( table.getColHeaders()[Integer.parseInt(indexOfColumnToRename)], newName2);
    }

    @Test
    public void ensureTargetColumnIsNumericOrBinaryCategoricalTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "ColD")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_STR, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b", "c"))
                .withDataForCol(1, ard(1, 2, 3))
                .withDataForCol(2, ar("2", "6", "6"))
                .withDataForCol(3, ar("2", "6", "6"))
                .build();

        TargetEncoder tec = new TargetEncoder();

        try {
            tec.ensureTargetColumnIsNumericOrBinaryCategorical(fr, 0);
            fail();
        } catch (Exception ex) {
            assertEquals( "`target` must be a binary vector", ex.getMessage());
        }

        try {
            tec.ensureTargetColumnIsNumericOrBinaryCategorical(fr, 2);
            fail();
        } catch (Exception ex) {
            assertEquals( "`target` must be a numeric or binary vector", ex.getMessage());
        }

        // Check that numerical column is ok
        tec.ensureTargetColumnIsNumericOrBinaryCategorical(fr, 1);

        // Check that binary categorical is ok (transformation is checked in another test)
        tec.ensureTargetColumnIsNumericOrBinaryCategorical(fr, 3);
    }

    @Test
    public void transformBinaryTargetColumnTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b"))
                .withDataForCol(1, ard(1, 1))
                .withDataForCol(2, ar("2", "6"))
                .withDataForCol(3, ar(1, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();

        TwoDimTable twoDimTable = fr.toTwoDimTable();
        System.out.println(twoDimTable.toString());

        Frame res = tec.transformBinaryTargetColumn(fr, 2);

        TwoDimTable twoDimTable2 = res.toTwoDimTable();
        System.out.println(twoDimTable2.toString());

        Vec transformedVector = res.vec(2);
        assertTrue(transformedVector.isNumeric());
        assertEquals(0, transformedVector.at(0), 1e-5);
        assertEquals(1, transformedVector.at(1), 1e-5);
    }

    @Test
    public void targetEncoderGetOutOfFoldDataTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ard(5, 6, 7, 9))
                .withDataForCol(1, ard(1, 2, 3, 1))
                .build();

        TargetEncoder tec = new TargetEncoder();

        Frame outOfFoldData = tec.getOutOfFoldData(fr, "1", 1);
        TwoDimTable twoDimTable = outOfFoldData.toTwoDimTable();
        assertEquals(outOfFoldData.numRows(), 2);

        assertEquals(6L, twoDimTable.get(5, 0));
        assertEquals(7L, twoDimTable.get(6, 0));

        Frame outOfFoldData2 = tec.getOutOfFoldData(fr, "1", 2);
        TwoDimTable twoDimTable2 = outOfFoldData2.toTwoDimTable();

        assertEquals(5L, twoDimTable2.get(5, 0));
        assertEquals(7L, twoDimTable2.get(6, 0));
        assertEquals(9L, twoDimTable2.get(7, 0));

    }

    @Test
    public void targetEncoderIsWorkingWithRealDataSetsTest() {

        Key parsed = Key.make("airlines_parsed");
        Key parsedTest = Key.make("airlines_test_parsed");

        Frame airlinesTrainFrame = parse_test_file(parsed, "smalldata/airlines/AirlinesTrain.csv.zip");
        Frame airlinesTestFrame = parse_test_file(parsedTest, "smalldata/airlines/AirlinesTest.csv.zip");

        for( String header : airlinesTrainFrame.toTwoDimTable().getColHeaders()) {
            String type = airlinesTrainFrame.vec(header).get_type_str();
            int cardinality = airlinesTrainFrame.vec(header).cardinality();
            System.out.println(header + " - " + type + String.format("; Cardinality = %d", cardinality));

        }

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {7}; // 7 stands for Origin column

        Map<String, Frame> encodingMap = tec.prepareEncodingMap(airlinesTrainFrame, teColumns, 10); // 10 stands for IsDepDelayed column
        Frame trainEncoded = tec.applyTargetEncoding(airlinesTrainFrame, teColumns, 10, encodingMap, TargetEncoder.HoldoutType.None,false, 0, 1234.0);


        // Preparing test frame
        Frame testEncoded = tec.applyTargetEncoding(airlinesTestFrame, teColumns, 10, encodingMap, TargetEncoder.HoldoutType.None,false, 0, 1234.0);

        testEncoded = tec.ensureTargetColumnIsNumericOrBinaryCategorical(testEncoded, 10); // TODO we  need here pseudobinary numerical(quasibinomial).

//        testEncoded = FrameUtils.categoricalEncoder(testEncoded, new String[]{}, Model.Parameters.CategoricalEncodingScheme.Enum, null, -1);
//        Vec categColumnTest = testEncoded.vec("IsDepDelayed").toCategoricalVec();
//        testEncoded.remove("IsDepDelayed");
//        testEncoded.add("IsDepDelayed", categColumnTest);


        for( String header : testEncoded.toTwoDimTable().getColHeaders()) {
            String type = testEncoded.vec(header).get_type_str();
            int cardinality = testEncoded.vec(header).cardinality();
            System.out.println(header + " - " + type + String.format("; Cardinality = %d", cardinality));
        }


        printOutFrameAsTable(airlinesTrainFrame);
        printOutFrameAsTable(trainEncoded);
        printOutFrameAsTable(testEncoded);

        // With target encoded Origin column

        GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
        parms._train = trainEncoded._key;
        parms._response_column = "IsDepDelayed";
        parms._ntrees = 20;
        parms._max_depth = 3;
        parms._distribution = DistributionFamily.quasibinomial;
        parms._keep_cross_validation_predictions=true;
        parms._valid = testEncoded._key;
        parms._ignored_columns = new String[]{"IsDepDelayed_REC", "Origin"};
        GBM job = new GBM(parms);
        GBMModel gbm = job.trainModel().get();

        Assert.assertTrue(job.isStopped());

        hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.valid());
        double auc = mm._auc._auc;


        // WITHOUT
        Key parsed2 = Key.make("airlines_parsed2");

        Frame airlinesTrainFrame2 = parse_test_file(parsed2, "smalldata/airlines/AirlinesTrain.csv.zip");

        // DO we convert to quasibinomial properly? maybe in training set we get opposite values to the test set because we are converting separately.
        airlinesTrainFrame2 = tec.ensureTargetColumnIsNumericOrBinaryCategorical(airlinesTrainFrame2, 10); // TODO we  need here pseudobinary numerical(quasibinomial).

        int originCardinality = testEncoded.vec("Origin").cardinality();

        GBMModel.GBMParameters parms2 = new GBMModel.GBMParameters();
        parms2._train = airlinesTrainFrame2._key;
        parms2._response_column = "IsDepDelayed";
        parms2._ntrees = 20;
        parms2._max_depth = 3;
        parms2._distribution = DistributionFamily.quasibinomial;
        parms2._keep_cross_validation_predictions=true;
        parms2._valid = testEncoded._key;
        parms2._ignored_columns = new String[]{"IsDepDelayed_REC"};
        GBM job2 = new GBM(parms2);
        GBMModel gbm2 = job2.trainModel().get();

        Assert.assertTrue(job2.isStopped());

        hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm2, parms2.valid());
        double auc2 = mm2._auc._auc;

        System.out.println("Origin cardinality:" + originCardinality);
        System.out.println("AUC with encoding:" + auc);
        System.out.println("AUC without encoding:" + auc2);

        // Fails. Maybe it is not of that high cardinality and target encoding actually hurts. Maybe not in terms of speed but in terms of performance.
        Assert.assertTrue(auc2 < auc );
    }

    @After
    public void afterEach() {
        System.out.println("After each test we do H2O.STORE.clear() and Vec.ESPC.clear()");
        Vec.ESPC.clear();
        // TODO in checkLeakedKeys method from TestUntil we are purging store anyway. So maybe we should add default cleanup? or we need to inform developer about specific leakages?
        H2O.STORE.clear();
    }

    // TODO remove.
    private void printOutFrameAsTable(Frame fr) {

        TwoDimTable twoDimTable = fr.toTwoDimTable();
        System.out.println(twoDimTable.toString());
    }
}

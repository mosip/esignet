package io.mosip.testrig.apirig.esignet.testscripts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.testng.ITest;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import io.mosip.testrig.apirig.dbaccess.DBManager;
import io.mosip.testrig.apirig.dto.OutputValidationDto;
import io.mosip.testrig.apirig.dto.TestCaseDTO;
import io.mosip.testrig.apirig.esignet.utils.EsignetConfigManager;
import io.mosip.testrig.apirig.esignet.utils.EsignetUtil;
import io.mosip.testrig.apirig.testrunner.HealthChecker;
import io.mosip.testrig.apirig.utils.AdminTestException;
import io.mosip.testrig.apirig.utils.AuthenticationTestException;
import io.mosip.testrig.apirig.utils.GlobalConstants;
import io.mosip.testrig.apirig.utils.OutputValidationUtil;
import io.mosip.testrig.apirig.utils.SecurityXSSException;
import io.restassured.response.Response;

public class DBValidator extends EsignetUtil implements ITest {
	private static final Logger logger = Logger.getLogger(DBValidator.class);
	protected String testCaseName = "";
	public static List<String> templateFields = new ArrayList<>();
	public Response response = null;
	private static final int MAX_RETRY_COUNT = 5;
	private static final long RETRY_DELAY_MS = 2000;

	@BeforeClass
	public static void setLogLevel() {
		if (EsignetConfigManager.IsDebugEnabled())
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.ERROR);
	}

	/**
	 * get current testcaseName
	 */
	@Override
	public String getTestName() {
		return testCaseName;
	}

	/**
	 * Data provider class provides test case list
	 * 
	 * @return object of data provider
	 */
	@DataProvider(name = "testcaselist")
	public Object[] getTestCaseList(ITestContext context) {
		String ymlFile = context.getCurrentXmlTest().getLocalParameters().get("ymlFile");
		logger.info("Started executing yml: " + ymlFile);
		return getYmlTestData(ymlFile);
	}

	@Test(dataProvider = "testcaselist")
	public void test(TestCaseDTO testCaseDTO) throws AuthenticationTestException, AdminTestException, SecurityXSSException {
		testCaseName = testCaseDTO.getTestCaseName();
		testCaseName = EsignetUtil.isTestCaseValidForExecution(testCaseDTO);
		if (HealthChecker.signalTerminateExecution) {
			throw new SkipException(
					GlobalConstants.TARGET_ENV_HEALTH_CHECK_FAILED + HealthChecker.healthCheckFailureMapS);
		}

		String inputJson = getJsonFromTemplate(testCaseDTO.getInput(), testCaseDTO.getInputTemplate());
		String replaceId = inputJsonKeyWordHandeler(inputJson, testCaseName);

		JSONObject jsonObject = new JSONObject(replaceId);
		logger.info(jsonObject.keySet());
		if (jsonObject.length() != 1) {
			throw new AdminTestException("DBValidator input must contain exactly one filter field");
		}
		Set<String> set = new TreeSet<>(jsonObject.keySet());
		String filterId = set.iterator().next();

		logger.info(filterId);
		String query = testCaseDTO.getEndPoint() + " " + filterId + " = " + "'" + jsonObject.getString(filterId) + "'";

		logger.info(query);
		Map<String, Object> response = DBManager.executeQueryAndGetRecord(testCaseDTO.getRole(), query);

		int retryCount = 0;
		while (response.isEmpty() && retryCount < MAX_RETRY_COUNT) {
			retryCount++;
			logger.info("No record found yet, retrying (" + retryCount + "/" + MAX_RETRY_COUNT + ") after "
					+ RETRY_DELAY_MS + "ms: " + query);
			try {
				Thread.sleep(RETRY_DELAY_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
			response = DBManager.executeQueryAndGetRecord(testCaseDTO.getRole(), query);
		}

		Map<String, List<OutputValidationDto>> objMap = new HashMap<>();
		List<OutputValidationDto> objList = new ArrayList<>();
		OutputValidationDto objOpDto = new OutputValidationDto();
		if (response.size() > 0) {

			objOpDto.setStatus("PASS");
		} else {
			objOpDto.setStatus(GlobalConstants.FAIL_STRING);
		}

		objList.add(objOpDto);
		objMap.put(GlobalConstants.EXPECTED_VS_ACTUAL, objList);

		if (!OutputValidationUtil.publishOutputResult(objMap))
			throw new AdminTestException("Failed at output validation");
	}

	/**
	 * The method ser current test name to result
	 * 
	 * @param result
	 */
	@AfterMethod(alwaysRun = true)
	public void setResultTestName(ITestResult result) {
		result.setAttribute("TestCaseName", testCaseName);
	}
}

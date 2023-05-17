package io.mosip.authentication.esignet.integration.config;

import org.mockito.Mockito;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;

import io.mosip.authentication.common.service.cache.MasterDataCache;
import io.mosip.authentication.common.service.config.IDAMappingConfig;
import io.mosip.authentication.common.service.factory.RestRequestFactory;
import io.mosip.authentication.common.service.helper.IdInfoHelper;
import io.mosip.authentication.common.service.helper.RestHelper;
import io.mosip.authentication.common.service.impl.IdInfoFetcherImpl;
import io.mosip.authentication.common.service.integration.MasterDataManager;
import io.mosip.authentication.common.service.integration.OTPManager;
import io.mosip.authentication.common.service.repository.OtpTxnRepository;
import io.mosip.authentication.common.service.repository.UinHashSaltRepo;
import io.mosip.authentication.common.service.transaction.manager.IdAuthSecurityManager;
import io.mosip.authentication.core.exception.IdAuthenticationBusinessException;
import io.mosip.authentication.core.spi.demoauth.DemoNormalizer;
import io.mosip.authentication.core.spi.indauth.match.IdInfoFetcher;
import io.mosip.authentication.core.spi.notification.service.NotificationService;
//import io.mosip.authentication.common.service.factory.RestRequestFactory;
//import io.mosip.authentication.common.service.helper.IdInfoHelper;
//import io.mosip.authentication.common.service.helper.RestHelper;
//import io.mosip.authentication.common.service.integration.MasterDataManager;
//import io.mosip.authentication.common.service.integration.OTPManager;
//import io.mosip.authentication.common.service.repository.OtpTxnRepository;
//import io.mosip.authentication.common.service.repository.UinHashSaltRepo;
//import io.mosip.authentication.common.service.transaction.manager.IdAuthSecurityManager;
import io.mosip.authentication.esignet.integration.service.TokenIdManager;
import io.mosip.kernel.cbeffutil.impl.CbeffImpl;
import io.mosip.kernel.core.cbeffutil.spi.CbeffUtil;
import io.mosip.kernel.cryptomanager.service.CryptomanagerService;
import io.mosip.kernel.cryptomanager.service.impl.CryptomanagerServiceImpl;
import io.mosip.kernel.tokenidgenerator.generator.TokenIDGenerator;
import io.mosip.kernel.tokenidgenerator.service.TokenIDGeneratorService;
import io.mosip.kernel.tokenidgenerator.service.impl.TokenIDGeneratorServiceImpl;
import io.mosip.kernel.zkcryptoservice.service.spi.ZKCryptoManagerService;

@EnableCaching
@Configuration
//TODO commented assuming auth only used
@Import(value= {IDAMappingConfig.class})
public class IdaConfig {

	@Bean
	public TokenIDGeneratorService getTokenIdGeneratorService() {
		return new TokenIDGeneratorServiceImpl();
	}
	
	@Bean
	public TokenIdManager getTokenIdManager() {
		return new TokenIdManager();
	}

	@Bean
	public TokenIDGenerator getTokenIdGenerator() {
		return new TokenIDGenerator();
	}

	@Bean
	public CryptomanagerService getCryptomanagerService() {
		return new CryptomanagerServiceImpl();
	}
	
	@Lazy
	@Bean
	public IdInfoHelper getIdInfoHelper() {
		return new IdInfoHelper();
	}

	@Lazy
	@Bean
	public IdInfoFetcher getIdInfoFetcher() {
		return new IdInfoFetcherImpl();
	}

	@Lazy
	@Bean
	public OTPManager getOTPManager() {
		return new OTPManager();
	}

	@Lazy
	@Bean
	public CbeffUtil getCbeffUtil() {
		return new CbeffImpl();
	}

	@Lazy
	@Bean
	public MasterDataManager getMasterDataManager() {
		return new MasterDataManager();
	}

	@Lazy
	@Bean
	public MasterDataCache getMasterDataCache() {
		return new MasterDataCache() {
			@Override
			public void loadMasterData() throws IdAuthenticationBusinessException {
				//Do nothing
			}
		};
	}

	@Lazy
	@Bean
	public RestRequestFactory getRestRequestFactory() {
		return new RestRequestFactory();
	}

	@Lazy
	@Bean
	public IdAuthSecurityManager getIdAuthSecurityManager() {
		return new IdAuthSecurityManager();
	}
	


	@Bean("external")
	public RestHelper getRestHelper() {
		// Just using mock rest helper as it is not used here
		return Mockito.mock(RestHelper.class);
	}

	@Bean
	public NotificationService getNotificationService() {
		// Just using mock rest helper as it is not used here
		return Mockito.mock(NotificationService.class);
	}

	@Bean
	public UinHashSaltRepo getUinHashSaltRepo() {
		// Just using mock rest helper as it is not used here
		return Mockito.mock(UinHashSaltRepo.class);
	}

	@Bean
	public DemoNormalizer getDemoNormalizer() {
		// Just using mock rest helper as it is not used here
		return Mockito.mock(DemoNormalizer.class);
	}

	@Bean
	public ZKCryptoManagerService getZKCryptoManagerService() {
		// Just using mock rest helper as it is not used here
		return Mockito.mock(ZKCryptoManagerService.class);
	}

	@Bean
	public OtpTxnRepository getOtpTxnRepository() {
		// Just using mock rest helper as it is not used here
		return Mockito.mock(OtpTxnRepository.class);
	}

}

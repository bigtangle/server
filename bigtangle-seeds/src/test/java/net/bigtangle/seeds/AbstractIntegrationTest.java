/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.seeds;


import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.web.context.WebApplicationContext;
 
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {})

@TestExecutionListeners(value = { DependencyInjectionTestExecutionListener.class, MockitoTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class

})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    public final String CONTEXT_ROOT_TEMPLATE = "http://localhost:%s/";
    // private static final Logger log =
    // LoggerFactory.getLogger(AbstractIntegrationTest.class);
    public String contextRoot; 

    @Autowired
    protected WebApplicationContext webContext;

    @Autowired
    private ConfigurableApplicationContext applicationContext;
      
    @Autowired
    public void prepareContextRoot(@Value("${local.server.port}") int port) {
        contextRoot = String.format(CONTEXT_ROOT_TEMPLATE, port);
    }
 


    public String getContextRoot() {
        return contextRoot;
    }

    public ConfigurableApplicationContext getApplicationContext() {
        return applicationContext;
    }

}

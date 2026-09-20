package com.jasonwidjaja.dvp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.RestController;

import com.jasonwidjaja.dvp.persistence.AccountRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class DvpApplicationTests {

    @MockitoBean
    private NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBean(DvpApplication.class)).isNotNull();
        assertThat(applicationContext.getBean(AccountRepository.class)).isNotNull();
        assertThat(applicationContext.getBeanNamesForAnnotation(RestController.class))
                .containsExactlyInAnyOrder("tradeController", "accountController");
    }
}

package br.com.geangc.sistema_mr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude =  {
    org.springframework.ai.model.chat.memory.repository.neo4j.autoconfigure.Neo4jChatMemoryRepositoryAutoConfiguration.class
})
public class SistemaMrApplication {

	public static void main(String[] args) {
		SpringApplication.run(SistemaMrApplication.class, args);
	}

}

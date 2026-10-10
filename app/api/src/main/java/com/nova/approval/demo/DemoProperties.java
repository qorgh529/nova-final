package com.nova.approval.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * demo 프로파일 설정 (nova.demo.*). 기본값은 application-demo.yml 참고.
 */
@ConfigurationProperties(prefix = "nova.demo")
public class DemoProperties {

    private Seed seed = new Seed();
    private Generator generator = new Generator();

    public Seed getSeed() {
        return seed;
    }

    public void setSeed(Seed seed) {
        this.seed = seed;
    }

    public Generator getGenerator() {
        return generator;
    }

    public void setGenerator(Generator generator) {
        this.generator = generator;
    }

    public static class Seed {
        /** 만들 데모 사용자 수 (demo01 ~ demoNN). 기본 계정(admin 등)과 별개다 */
        private int users = 20;
        /** 문서가 이 수보다 적을 때만 모자란 만큼 만든다 */
        private int documents = 300;
        /** EICAR 테스트 파일이 든 첨부를 만들지 여부 */
        private boolean eicar = true;

        public int getUsers() {
            return users;
        }

        public void setUsers(int users) {
            this.users = users;
        }

        public int getDocuments() {
            return documents;
        }

        public void setDocuments(int documents) {
            this.documents = documents;
        }

        public boolean isEicar() {
            return eicar;
        }

        public void setEicar(boolean eicar) {
            this.eicar = eicar;
        }
    }

    public static class Generator {
        private boolean enabled = true;
        /** 생성 주기. RPO 측정 해상도가 이 값이 된다 */
        private Duration interval = Duration.ofSeconds(10);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration interval) {
            this.interval = interval;
        }
    }
}

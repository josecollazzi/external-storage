package com.boomi.flow.external.storage;

import com.boomi.flow.external.storage.guice.StateRepositoryProvider;
import com.boomi.flow.external.storage.keys.KeyRepository;
import com.boomi.flow.external.storage.keys.KeyRepositoryProvider;
import com.boomi.flow.external.storage.states.StateRepository;
import com.google.inject.AbstractModule;
import com.manywho.sdk.services.servers.Servlet3Server;
import org.jdbi.v3.core.Jdbi;

import javax.inject.Singleton;

public class ApplicationTest extends Servlet3Server {

    public ApplicationTest(Jdbi jdbiSchema) {
        this.addModule(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Jdbi.class).toInstance(jdbiSchema);
                bind(KeyRepository.class).toProvider(KeyRepositoryProvider.class).in(Singleton.class);
                bind(StateRepository.class).toProvider(StateRepositoryProvider.class).in(Singleton.class);
            }
        });

        this.setApplication(ApplicationTest.class);
        this.start();
    }
}

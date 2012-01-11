package net.intelie.lognit.cli;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import net.intelie.lognit.cli.commands.Command;
import net.intelie.lognit.cli.commands.Info;
import net.intelie.lognit.cli.commands.Login;
import net.intelie.lognit.cli.state.CookieStorage;
import org.apache.commons.httpclient.HttpClient;

import java.io.File;

import static com.google.inject.name.Names.named;

public class MainModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(HttpClient.class).in(Singleton.class);
        
        bind(Command[].class).toProvider(commands());
        
        bind(CookieStorage.class).toInstance(
                new CookieStorage(new File(System.getProperty("user.home"), ".lognit/cookies")));

    }

    private Provider<Command[]> commands() {
        return new Provider<Command[]>() {
            @Inject
            private Login login;
            @Inject
            private Info info;
            @Override
            public Command[] get() {
                return new Command[] {
                    login, info
                };
            }
        };
    }
}
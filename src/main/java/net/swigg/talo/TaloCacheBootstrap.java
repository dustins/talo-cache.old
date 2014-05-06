/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Dustin Sweigart
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.swigg.talo;

import net.swigg.talo.admin.config.AdminConfig;
import net.swigg.talo.proxy.TaloCacheServlet;
import org.apache.commons.cli.*;
import org.apache.commons.configuration.ConfigurationException;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.net.InetSocketAddress;

/**
 * The main entry point for TALOCache that bootstraps the application.
 *
 * @author Dustin Sweigart <dustin@swigg.net>
 */
public class TaloCacheBootstrap {
    static private final Logger LOGGER = LoggerFactory.getLogger(TaloCacheBootstrap.class);
    private BootstrapConfig config;
    private Server adminServer;
    private Server proxyServer;

    public static void main(String[] args) throws ConfigurationException, ParseException {
        Options options = new Options();
        options.addOption("help", false, "help");
        options.addOption("listenHost", true, "interface to listen on");
        options.addOption("listenPort", true, "port to listen on");
        options.addOption("targetPrefix", true, "where to proxy to");

        CommandLineParser parser = new BasicParser();
        CommandLine command = parser.parse( options, args);

        if (command.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "talocache", options );
            return;
        }

        BootstrapConfig config = new BootstrapConfig();

        config.listenHost = command.getOptionValue("listenHost", config.listenHost);
        config.listenPort = Integer.parseInt(command.getOptionValue("listenPort", config.listenPort.toString()));
        config.targetPrefix = command.getOptionValue("targetPrefix", config.targetPrefix);

        TaloCacheBootstrap taloCache = new TaloCacheBootstrap(config);

        taloCache.start();
    }

    private void start() {
        this.adminServer = new Server(6060);
        this.adminServer.setHandler(createAdminHandler(createAdminContext()));

        try {
            this.adminServer.start();
        } catch (Exception e) {
            LOGGER.error("An error occurred while starting up the administration manager.", e);
        }

        InetSocketAddress proxyAddress = new InetSocketAddress(config.listenHost, config.listenPort);
        this.proxyServer = new Server(proxyAddress);
        this.proxyServer.setHandler(createProxyHandler());

        try {
            this.proxyServer.start();
            this.proxyServer.join();
        } catch (Exception e) {
            LOGGER.error("An error occurred while starting.", e);
        }
    }

    private Handler createProxyHandler() {
        ServletContextHandler contextHandler = new ServletContextHandler();
        contextHandler.setErrorHandler(null);
        contextHandler.setContextPath(this.config.contextPath);

        ServletHolder servletHolder = new ServletHolder(new TaloCacheServlet());
        contextHandler.addServlet(servletHolder, "/*");
        servletHolder.setInitParameter("proxyTo", config.targetPrefix);
        servletHolder.setInitParameter("prefix", "/");

        return contextHandler;
    }

    private Handler createAdminHandler(WebApplicationContext context) {
        ServletContextHandler contextHandler = new ServletContextHandler();
        contextHandler.setErrorHandler(null);
        contextHandler.setContextPath("/");
        ServletHolder servletHolder = new ServletHolder(new DispatcherServlet(context));
        contextHandler.addServlet(servletHolder, "/*");
        contextHandler.addEventListener(new ContextLoaderListener(context));

        return contextHandler;
    }

    private WebApplicationContext createAdminContext() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setConfigLocation(AdminConfig.class.toString());
        context.getEnvironment().setDefaultProfiles(this.config.environment);
        return context;
    }

    public TaloCacheBootstrap() {
        this(new BootstrapConfig());
    }

    public TaloCacheBootstrap(BootstrapConfig config) {
        this.config = config;
    }

    public static class BootstrapConfig {
        private String contextPath = "/";
        private String mappingUri  = "/*";

        private String  listenHost = "localhost";
        private Integer listenPort    = 8080;

        private String targetPrefix = "http://localhost:6060/";

        private String[] environment = {"development"};
    }
}

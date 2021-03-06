/*
 * Copyright 2014 astamuse company,Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package com.astamuse.asta4d.misc.spring;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.astamuse.asta4d.web.WebApplicationConfiguration;
import com.astamuse.asta4d.web.servlet.Asta4dServlet;

public class SpringInitializableServlet extends Asta4dServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private ApplicationContext springContext = null;

    @Override
    protected WebApplicationConfiguration createConfiguration() {
        springContext = createSpringContext(retrieveSpringConfigLocation());
        return springContext.getBean(WebApplicationConfiguration.class);
    }

    protected ApplicationContext createSpringContext(String location) {
        return new ClassPathXmlApplicationContext(location);
    }

    protected String retrieveSpringConfigLocation() {
        String configLocation = getServletConfig().getInitParameter("contextConfigLocation");
        if (configLocation.startsWith("classpath:")) {
            configLocation = configLocation.substring("classpath:".length());
        }
        if (!configLocation.startsWith("/")) {
            configLocation = "/" + configLocation;
        }
        return configLocation;
    }

    protected ApplicationContext getSpringContext() {
        return springContext;
    }

    @Override
    public void destroy() {
        super.destroy();
        ((ConfigurableApplicationContext) springContext).close();
        springContext = null;

    }
}

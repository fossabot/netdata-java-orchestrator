/*
 * Copyright (C) 2017 Simon Nagl
 *
 * netdata-java-orchestrator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.firehol.netdata.orchestrator;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.firehol.netdata.Main;
import org.firehol.netdata.exception.InitializationException;
import org.firehol.netdata.model.Chart;
import org.firehol.netdata.module.Module;
import org.firehol.netdata.utils.AlignToTimeIntervalService;
import org.firehol.netdata.utils.LoggingUtils;

public class Orchestrator implements Collector {
	private static final Logger log = Logger.getLogger("org.firehol.netdata.orchestrator");

	private final int updateEverySecond;

	private final List<Module> modules;

	public Orchestrator(int updateEveryInSeconds, List<Module> modules) {
		this.updateEverySecond = updateEveryInSeconds;
		this.modules = modules;
	}

	public void start() {
		initializeModules();
		runMainLoop();
	}

	private void initializeModules() {
		try {
			Collection<Chart> chartsToInitialize = initialize();
			for (Chart chart : chartsToInitialize) {
				Printer.initializeChart(chart);
			}
		} catch (Exception e) {
			Main.exit(LoggingUtils.buildMessage("Could not initialize. Disabling Java Orchestrator.", e));
		}

	}

	@Override
	public Collection<Chart> initialize() throws InitializationException {
		Collection<Chart> chartsToInitialize = new LinkedList<>();

		Iterator<Module> moduleIterator = modules.iterator();
		while (moduleIterator.hasNext()) {
			Module module = moduleIterator.next();
			try {
				chartsToInitialize.addAll(module.initialize());
			} catch (InitializationException e) {
				moduleIterator.remove();
				log.warning(LoggingUtils.getMessageSupplier("Could not initialize module " + module.getName(), e));
			}
		}

		if (chartsToInitialize.size() < 1) {
			throw new InitializationException("No Charts to initialize.");
		}

		return chartsToInitialize;
	}

	private void runMainLoop() {
		AlignToTimeIntervalService timeService = new AlignToTimeIntervalService(updateEverySecond, TimeUnit.SECONDS);
		while (true) {
			timeService.alignToNextInterval();

			collectValues().stream().forEach(Printer::collect);
		}
	}

	@Override
	public Collection<Chart> collectValues() {
		return modules.stream().map(Module::collectValues).flatMap(Collection::stream).collect(Collectors.toList());
	}

	@Override
	public void cleanup() {
		for (Module module : modules) {
			module.cleanup();
		}
	}
}

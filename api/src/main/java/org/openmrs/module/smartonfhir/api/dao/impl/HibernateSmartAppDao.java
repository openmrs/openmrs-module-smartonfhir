/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.api.dao.impl;

import java.util.List;

import lombok.Setter;
import org.hibernate.Criteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.smartonfhir.api.dao.SmartAppDao;
import org.openmrs.module.smartonfhir.model.SmartApp;

@Setter
public class HibernateSmartAppDao implements SmartAppDao {

	private DbSessionFactory sessionFactory;

	@Override
	public SmartApp getByUuid(String uuid) {
		return (SmartApp) criteria().add(Restrictions.eq("uuid", uuid)).uniqueResult();
	}

	@Override
	public SmartApp getByName(String name) {
		return (SmartApp) criteria().add(Restrictions.eq("name", name)).uniqueResult();
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<SmartApp> getAll(boolean includeRetired) {
		Criteria criteria = criteria();

		if (!includeRetired) {
			criteria.add(Restrictions.eq("retired", false));
		}

		return criteria.addOrder(Order.asc("name")).list();
	}

	@Override
	public SmartApp saveOrUpdate(SmartApp app) {
		sessionFactory.getCurrentSession().saveOrUpdate(app);
		return app;
	}

	@Override
	public void delete(SmartApp app) {
		sessionFactory.getCurrentSession().delete(app);
	}

	private Criteria criteria() {
		return sessionFactory.getCurrentSession().createCriteria(SmartApp.class);
	}
}

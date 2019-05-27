/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.commerce.product.internal.upgrade.v1_3_0;

import com.liferay.commerce.product.model.impl.CPDefinitionModelImpl;
import com.liferay.commerce.product.model.impl.CProductModelImpl;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.dao.jdbc.AutoBatchPreparedStatementUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.upgrade.UpgradeProcess;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.uuid.PortalUUIDUtil;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * @author Ethan Bustad
 * @author Alessio Antonio Rendina
 */
public class CProductUpgradeProcess extends UpgradeProcess {

	@Override
	protected void doUpgrade() throws Exception {
		if (!hasTable(CProductModelImpl.TABLE_NAME)) {
			runSQL(CProductModelImpl.TABLE_SQL_CREATE);
		}

		_addColumn(
			CPDefinitionModelImpl.class, CPDefinitionModelImpl.TABLE_NAME,
			"CProductId", "LONG");
		_addColumn(
			CPDefinitionModelImpl.class, CPDefinitionModelImpl.TABLE_NAME,
			"version", "INTEGER");

		String insertCProductSQL = StringBundler.concat(
			"insert into CProduct (uuid_, CProductId, groupId, companyId, ",
			"userId, userName, createDate, modifiedDate, ",
			"publishedCPDefinitionId, latestVersion) values (?, ?, ?, ?, ?, ",
			"?, ?, ?, ?, 1)");
		String updateCPDefinitionSQL =
			"update CPDefinition set CProductId = ?, version = 1 where " +
				"CPDefinitionId = ?";

		try (PreparedStatement ps1 =
				AutoBatchPreparedStatementUtil.concurrentAutoBatch(
					connection, insertCProductSQL);
			PreparedStatement ps2 =
				AutoBatchPreparedStatementUtil.concurrentAutoBatch(
					connection, updateCPDefinitionSQL);
			Statement s = connection.createStatement(
				ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			ResultSet rs = s.executeQuery(
				"select cpDefinitionId, groupId, companyId, userId, userName " +
					"from CPDefinition")) {

			while (rs.next()) {
				String uuid = PortalUUIDUtil.generate();
				long cProductId = increment();
				long groupId = rs.getLong("groupId");
				long companyId = rs.getLong("companyId");
				long userId = rs.getLong("userId");
				String userName = rs.getString("userName");
				long cpDefinitionId = rs.getLong("CPDefinitionId");

				ps1.setString(1, uuid);
				ps1.setLong(2, cProductId);
				ps1.setLong(3, groupId);
				ps1.setLong(4, companyId);
				ps1.setLong(5, userId);
				ps1.setString(6, userName);

				Date now = new Date(System.currentTimeMillis());

				ps1.setDate(7, now);
				ps1.setDate(8, now);

				ps1.setLong(9, cpDefinitionId);

				ps1.executeUpdate();

				ps2.setLong(1, cProductId);
				ps2.setLong(2, cpDefinitionId);

				ps2.executeUpdate();
			}

			ps1.executeBatch();
			ps2.executeBatch();
		}
	}

	private void _addColumn(
			Class<?> entityClass, String tableName, String columnName,
			String columnType)
		throws Exception {

		if (_log.isInfoEnabled()) {
			_log.info(
				String.format(
					"Adding column %s to table %s", columnName, tableName));
		}

		if (!hasColumn(tableName, columnName)) {
			alter(
				entityClass,
				new AlterTableAddColumn(
					columnName + StringPool.SPACE + columnType));
		}
		else {
			if (_log.isInfoEnabled()) {
				_log.info(
					String.format(
						"Column %s already exists on table %s", columnName,
						tableName));
			}
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CProductUpgradeProcess.class);

}
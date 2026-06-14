package com.local.questionbank.data.database;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.local.questionbank.data.database.dao.AnswerRecordDao;
import com.local.questionbank.data.database.dao.AnswerRecordDao_Impl;
import com.local.questionbank.data.database.dao.FavoriteDao;
import com.local.questionbank.data.database.dao.FavoriteDao_Impl;
import com.local.questionbank.data.database.dao.QuestionBankDao;
import com.local.questionbank.data.database.dao.QuestionBankDao_Impl;
import com.local.questionbank.data.database.dao.QuestionDao;
import com.local.questionbank.data.database.dao.QuestionDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile QuestionBankDao _questionBankDao;

  private volatile QuestionDao _questionDao;

  private volatile AnswerRecordDao _answerRecordDao;

  private volatile FavoriteDao _favoriteDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `question_bank` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `createTimestamp` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `question` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bankId` INTEGER NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `optionsJson` TEXT NOT NULL, `answerJson` TEXT NOT NULL, `analysis` TEXT, FOREIGN KEY(`bankId`) REFERENCES `question_bank`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_bankId` ON `question` (`bankId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `answer_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `questionId` INTEGER NOT NULL, `isCorrect` INTEGER NOT NULL, `answerTimestamp` INTEGER NOT NULL, FOREIGN KEY(`questionId`) REFERENCES `question`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_answer_record_questionId` ON `answer_record` (`questionId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_answer_record_answerTimestamp` ON `answer_record` (`answerTimestamp`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `favorite` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `questionId` INTEGER NOT NULL, `tag` TEXT NOT NULL, `createTimestamp` INTEGER NOT NULL, FOREIGN KEY(`questionId`) REFERENCES `question`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_questionId` ON `favorite` (`questionId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_createTimestamp` ON `favorite` (`createTimestamp`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '575fbca7c5174e78eeedab8257812b75')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `question_bank`");
        db.execSQL("DROP TABLE IF EXISTS `question`");
        db.execSQL("DROP TABLE IF EXISTS `answer_record`");
        db.execSQL("DROP TABLE IF EXISTS `favorite`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsQuestionBank = new HashMap<String, TableInfo.Column>(4);
        _columnsQuestionBank.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQuestionBank.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQuestionBank.put("description", new TableInfo.Column("description", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQuestionBank.put("createTimestamp", new TableInfo.Column("createTimestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysQuestionBank = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesQuestionBank = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoQuestionBank = new TableInfo("question_bank", _columnsQuestionBank, _foreignKeysQuestionBank, _indicesQuestionBank);
        final TableInfo _existingQuestionBank = TableInfo.read(db, "question_bank");
        if (!_infoQuestionBank.equals(_existingQuestionBank)) {
          return new RoomOpenHelper.ValidationResult(false, "question_bank(com.local.questionbank.data.database.entity.QuestionBankEntity).\n"
                  + " Expected:\n" + _infoQuestionBank + "\n"
                  + " Found:\n" + _existingQuestionBank);
        }
        final HashMap<String, TableInfo.Column> _columnsQuestion = new HashMap<String, TableInfo.Column>(7);
        _columnsQuestion.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQuestion.put("bankId", new TableInfo.Column("bankId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQuestion.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQuestion.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQuestion.put("optionsJson", new TableInfo.Column("optionsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQuestion.put("answerJson", new TableInfo.Column("answerJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQuestion.put("analysis", new TableInfo.Column("analysis", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysQuestion = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysQuestion.add(new TableInfo.ForeignKey("question_bank", "CASCADE", "NO ACTION", Arrays.asList("bankId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesQuestion = new HashSet<TableInfo.Index>(1);
        _indicesQuestion.add(new TableInfo.Index("index_question_bankId", false, Arrays.asList("bankId"), Arrays.asList("ASC")));
        final TableInfo _infoQuestion = new TableInfo("question", _columnsQuestion, _foreignKeysQuestion, _indicesQuestion);
        final TableInfo _existingQuestion = TableInfo.read(db, "question");
        if (!_infoQuestion.equals(_existingQuestion)) {
          return new RoomOpenHelper.ValidationResult(false, "question(com.local.questionbank.data.database.entity.QuestionEntity).\n"
                  + " Expected:\n" + _infoQuestion + "\n"
                  + " Found:\n" + _existingQuestion);
        }
        final HashMap<String, TableInfo.Column> _columnsAnswerRecord = new HashMap<String, TableInfo.Column>(4);
        _columnsAnswerRecord.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnswerRecord.put("questionId", new TableInfo.Column("questionId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnswerRecord.put("isCorrect", new TableInfo.Column("isCorrect", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnswerRecord.put("answerTimestamp", new TableInfo.Column("answerTimestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAnswerRecord = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysAnswerRecord.add(new TableInfo.ForeignKey("question", "CASCADE", "NO ACTION", Arrays.asList("questionId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesAnswerRecord = new HashSet<TableInfo.Index>(2);
        _indicesAnswerRecord.add(new TableInfo.Index("index_answer_record_questionId", false, Arrays.asList("questionId"), Arrays.asList("ASC")));
        _indicesAnswerRecord.add(new TableInfo.Index("index_answer_record_answerTimestamp", false, Arrays.asList("answerTimestamp"), Arrays.asList("ASC")));
        final TableInfo _infoAnswerRecord = new TableInfo("answer_record", _columnsAnswerRecord, _foreignKeysAnswerRecord, _indicesAnswerRecord);
        final TableInfo _existingAnswerRecord = TableInfo.read(db, "answer_record");
        if (!_infoAnswerRecord.equals(_existingAnswerRecord)) {
          return new RoomOpenHelper.ValidationResult(false, "answer_record(com.local.questionbank.data.database.entity.AnswerRecordEntity).\n"
                  + " Expected:\n" + _infoAnswerRecord + "\n"
                  + " Found:\n" + _existingAnswerRecord);
        }
        final HashMap<String, TableInfo.Column> _columnsFavorite = new HashMap<String, TableInfo.Column>(4);
        _columnsFavorite.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFavorite.put("questionId", new TableInfo.Column("questionId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFavorite.put("tag", new TableInfo.Column("tag", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFavorite.put("createTimestamp", new TableInfo.Column("createTimestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysFavorite = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysFavorite.add(new TableInfo.ForeignKey("question", "CASCADE", "NO ACTION", Arrays.asList("questionId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesFavorite = new HashSet<TableInfo.Index>(2);
        _indicesFavorite.add(new TableInfo.Index("index_favorite_questionId", true, Arrays.asList("questionId"), Arrays.asList("ASC")));
        _indicesFavorite.add(new TableInfo.Index("index_favorite_createTimestamp", false, Arrays.asList("createTimestamp"), Arrays.asList("ASC")));
        final TableInfo _infoFavorite = new TableInfo("favorite", _columnsFavorite, _foreignKeysFavorite, _indicesFavorite);
        final TableInfo _existingFavorite = TableInfo.read(db, "favorite");
        if (!_infoFavorite.equals(_existingFavorite)) {
          return new RoomOpenHelper.ValidationResult(false, "favorite(com.local.questionbank.data.database.entity.FavoriteEntity).\n"
                  + " Expected:\n" + _infoFavorite + "\n"
                  + " Found:\n" + _existingFavorite);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "575fbca7c5174e78eeedab8257812b75", "13130d1c7158c96e19add8a97160f339");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "question_bank","question","answer_record","favorite");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `question_bank`");
      _db.execSQL("DELETE FROM `question`");
      _db.execSQL("DELETE FROM `answer_record`");
      _db.execSQL("DELETE FROM `favorite`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(QuestionBankDao.class, QuestionBankDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(QuestionDao.class, QuestionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AnswerRecordDao.class, AnswerRecordDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(FavoriteDao.class, FavoriteDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public QuestionBankDao questionBankDao() {
    if (_questionBankDao != null) {
      return _questionBankDao;
    } else {
      synchronized(this) {
        if(_questionBankDao == null) {
          _questionBankDao = new QuestionBankDao_Impl(this);
        }
        return _questionBankDao;
      }
    }
  }

  @Override
  public QuestionDao questionDao() {
    if (_questionDao != null) {
      return _questionDao;
    } else {
      synchronized(this) {
        if(_questionDao == null) {
          _questionDao = new QuestionDao_Impl(this);
        }
        return _questionDao;
      }
    }
  }

  @Override
  public AnswerRecordDao answerRecordDao() {
    if (_answerRecordDao != null) {
      return _answerRecordDao;
    } else {
      synchronized(this) {
        if(_answerRecordDao == null) {
          _answerRecordDao = new AnswerRecordDao_Impl(this);
        }
        return _answerRecordDao;
      }
    }
  }

  @Override
  public FavoriteDao favoriteDao() {
    if (_favoriteDao != null) {
      return _favoriteDao;
    } else {
      synchronized(this) {
        if(_favoriteDao == null) {
          _favoriteDao = new FavoriteDao_Impl(this);
        }
        return _favoriteDao;
      }
    }
  }
}

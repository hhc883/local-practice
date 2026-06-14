package com.local.questionbank.data.database.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.local.questionbank.data.database.entity.QuestionEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class QuestionDao_Impl implements QuestionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<QuestionEntity> __insertionAdapterOfQuestionEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByBank;

  public QuestionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfQuestionEntity = new EntityInsertionAdapter<QuestionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `question` (`id`,`bankId`,`type`,`title`,`optionsJson`,`answerJson`,`analysis`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final QuestionEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getBankId());
        statement.bindString(3, entity.getType());
        statement.bindString(4, entity.getTitle());
        statement.bindString(5, entity.getOptionsJson());
        statement.bindString(6, entity.getAnswerJson());
        if (entity.getAnalysis() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getAnalysis());
        }
      }
    };
    this.__preparedStmtOfDeleteByBank = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM question WHERE bankId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertAll(final List<QuestionEntity> questions,
      final Continuation<? super List<Long>> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        __db.beginTransaction();
        try {
          final List<Long> _result = __insertionAdapterOfQuestionEntity.insertAndReturnIdsList(questions);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insert(final QuestionEntity question,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfQuestionEntity.insertAndReturnId(question);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteByBank(final long bankId, final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByBank.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, bankId);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteByBank.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<QuestionEntity>> observeByBankOrdered(final long bankId) {
    final String _sql = "SELECT * FROM question WHERE bankId = ? ORDER BY id ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, bankId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"question"}, new Callable<List<QuestionEntity>>() {
      @Override
      @NonNull
      public List<QuestionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBankId = CursorUtil.getColumnIndexOrThrow(_cursor, "bankId");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfOptionsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "optionsJson");
          final int _cursorIndexOfAnswerJson = CursorUtil.getColumnIndexOrThrow(_cursor, "answerJson");
          final int _cursorIndexOfAnalysis = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis");
          final List<QuestionEntity> _result = new ArrayList<QuestionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QuestionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpBankId;
            _tmpBankId = _cursor.getLong(_cursorIndexOfBankId);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpOptionsJson;
            _tmpOptionsJson = _cursor.getString(_cursorIndexOfOptionsJson);
            final String _tmpAnswerJson;
            _tmpAnswerJson = _cursor.getString(_cursorIndexOfAnswerJson);
            final String _tmpAnalysis;
            if (_cursor.isNull(_cursorIndexOfAnalysis)) {
              _tmpAnalysis = null;
            } else {
              _tmpAnalysis = _cursor.getString(_cursorIndexOfAnalysis);
            }
            _item = new QuestionEntity(_tmpId,_tmpBankId,_tmpType,_tmpTitle,_tmpOptionsJson,_tmpAnswerJson,_tmpAnalysis);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<QuestionEntity>> observeByBankRandom(final long bankId) {
    final String _sql = "SELECT * FROM question WHERE bankId = ? ORDER BY RANDOM()";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, bankId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"question"}, new Callable<List<QuestionEntity>>() {
      @Override
      @NonNull
      public List<QuestionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBankId = CursorUtil.getColumnIndexOrThrow(_cursor, "bankId");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfOptionsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "optionsJson");
          final int _cursorIndexOfAnswerJson = CursorUtil.getColumnIndexOrThrow(_cursor, "answerJson");
          final int _cursorIndexOfAnalysis = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis");
          final List<QuestionEntity> _result = new ArrayList<QuestionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QuestionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpBankId;
            _tmpBankId = _cursor.getLong(_cursorIndexOfBankId);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpOptionsJson;
            _tmpOptionsJson = _cursor.getString(_cursorIndexOfOptionsJson);
            final String _tmpAnswerJson;
            _tmpAnswerJson = _cursor.getString(_cursorIndexOfAnswerJson);
            final String _tmpAnalysis;
            if (_cursor.isNull(_cursorIndexOfAnalysis)) {
              _tmpAnalysis = null;
            } else {
              _tmpAnalysis = _cursor.getString(_cursorIndexOfAnalysis);
            }
            _item = new QuestionEntity(_tmpId,_tmpBankId,_tmpType,_tmpTitle,_tmpOptionsJson,_tmpAnswerJson,_tmpAnalysis);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object findById(final long questionId,
      final Continuation<? super QuestionEntity> $completion) {
    final String _sql = "SELECT * FROM question WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, questionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<QuestionEntity>() {
      @Override
      @Nullable
      public QuestionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBankId = CursorUtil.getColumnIndexOrThrow(_cursor, "bankId");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfOptionsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "optionsJson");
          final int _cursorIndexOfAnswerJson = CursorUtil.getColumnIndexOrThrow(_cursor, "answerJson");
          final int _cursorIndexOfAnalysis = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis");
          final QuestionEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpBankId;
            _tmpBankId = _cursor.getLong(_cursorIndexOfBankId);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpOptionsJson;
            _tmpOptionsJson = _cursor.getString(_cursorIndexOfOptionsJson);
            final String _tmpAnswerJson;
            _tmpAnswerJson = _cursor.getString(_cursorIndexOfAnswerJson);
            final String _tmpAnalysis;
            if (_cursor.isNull(_cursorIndexOfAnalysis)) {
              _tmpAnalysis = null;
            } else {
              _tmpAnalysis = _cursor.getString(_cursorIndexOfAnalysis);
            }
            _result = new QuestionEntity(_tmpId,_tmpBankId,_tmpType,_tmpTitle,_tmpOptionsJson,_tmpAnswerJson,_tmpAnalysis);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Long>> observeIncorrectQuestionIds() {
    final String _sql = "\n"
            + "        SELECT questionId FROM answer_record\n"
            + "        WHERE isCorrect = 0\n"
            + "        GROUP BY questionId\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"answer_record"}, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<Long> _result = new ArrayList<Long>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Long _item;
            _item = _cursor.getLong(0);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<QuestionEntity>> observeAllOrdered() {
    final String _sql = "SELECT * FROM question ORDER BY id ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"question"}, new Callable<List<QuestionEntity>>() {
      @Override
      @NonNull
      public List<QuestionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBankId = CursorUtil.getColumnIndexOrThrow(_cursor, "bankId");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfOptionsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "optionsJson");
          final int _cursorIndexOfAnswerJson = CursorUtil.getColumnIndexOrThrow(_cursor, "answerJson");
          final int _cursorIndexOfAnalysis = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis");
          final List<QuestionEntity> _result = new ArrayList<QuestionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QuestionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpBankId;
            _tmpBankId = _cursor.getLong(_cursorIndexOfBankId);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpOptionsJson;
            _tmpOptionsJson = _cursor.getString(_cursorIndexOfOptionsJson);
            final String _tmpAnswerJson;
            _tmpAnswerJson = _cursor.getString(_cursorIndexOfAnswerJson);
            final String _tmpAnalysis;
            if (_cursor.isNull(_cursorIndexOfAnalysis)) {
              _tmpAnalysis = null;
            } else {
              _tmpAnalysis = _cursor.getString(_cursorIndexOfAnalysis);
            }
            _item = new QuestionEntity(_tmpId,_tmpBankId,_tmpType,_tmpTitle,_tmpOptionsJson,_tmpAnswerJson,_tmpAnalysis);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<QuestionEntity>> observeAllRandom() {
    final String _sql = "SELECT * FROM question ORDER BY RANDOM()";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"question"}, new Callable<List<QuestionEntity>>() {
      @Override
      @NonNull
      public List<QuestionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBankId = CursorUtil.getColumnIndexOrThrow(_cursor, "bankId");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfOptionsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "optionsJson");
          final int _cursorIndexOfAnswerJson = CursorUtil.getColumnIndexOrThrow(_cursor, "answerJson");
          final int _cursorIndexOfAnalysis = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis");
          final List<QuestionEntity> _result = new ArrayList<QuestionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QuestionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpBankId;
            _tmpBankId = _cursor.getLong(_cursorIndexOfBankId);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpOptionsJson;
            _tmpOptionsJson = _cursor.getString(_cursorIndexOfOptionsJson);
            final String _tmpAnswerJson;
            _tmpAnswerJson = _cursor.getString(_cursorIndexOfAnswerJson);
            final String _tmpAnalysis;
            if (_cursor.isNull(_cursorIndexOfAnalysis)) {
              _tmpAnalysis = null;
            } else {
              _tmpAnalysis = _cursor.getString(_cursorIndexOfAnalysis);
            }
            _item = new QuestionEntity(_tmpId,_tmpBankId,_tmpType,_tmpTitle,_tmpOptionsJson,_tmpAnswerJson,_tmpAnalysis);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<QuestionEntity>> observeAllWrongQuestions() {
    final String _sql = "\n"
            + "        SELECT q.* FROM question q\n"
            + "        INNER JOIN (\n"
            + "            SELECT questionId, MAX(answerTimestamp) AS lastTs\n"
            + "            FROM answer_record\n"
            + "            WHERE isCorrect = 0\n"
            + "            GROUP BY questionId\n"
            + "        ) r ON r.questionId = q.id\n"
            + "        ORDER BY r.lastTs DESC\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"question",
        "answer_record"}, new Callable<List<QuestionEntity>>() {
      @Override
      @NonNull
      public List<QuestionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBankId = CursorUtil.getColumnIndexOrThrow(_cursor, "bankId");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfOptionsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "optionsJson");
          final int _cursorIndexOfAnswerJson = CursorUtil.getColumnIndexOrThrow(_cursor, "answerJson");
          final int _cursorIndexOfAnalysis = CursorUtil.getColumnIndexOrThrow(_cursor, "analysis");
          final List<QuestionEntity> _result = new ArrayList<QuestionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QuestionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpBankId;
            _tmpBankId = _cursor.getLong(_cursorIndexOfBankId);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpOptionsJson;
            _tmpOptionsJson = _cursor.getString(_cursorIndexOfOptionsJson);
            final String _tmpAnswerJson;
            _tmpAnswerJson = _cursor.getString(_cursorIndexOfAnswerJson);
            final String _tmpAnalysis;
            if (_cursor.isNull(_cursorIndexOfAnalysis)) {
              _tmpAnalysis = null;
            } else {
              _tmpAnalysis = _cursor.getString(_cursorIndexOfAnalysis);
            }
            _item = new QuestionEntity(_tmpId,_tmpBankId,_tmpType,_tmpTitle,_tmpOptionsJson,_tmpAnswerJson,_tmpAnalysis);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
